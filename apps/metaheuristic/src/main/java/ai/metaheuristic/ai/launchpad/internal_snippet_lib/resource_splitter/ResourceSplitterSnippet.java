/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.ai.launchpad.internal_snippet_lib.resource_splitter;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.BatchConfigYamlException;
import ai.metaheuristic.ai.exceptions.BatchProcessingException;
import ai.metaheuristic.ai.exceptions.BatchResourceProcessingException;
import ai.metaheuristic.ai.exceptions.StoreNewFileWithRedirectException;
import ai.metaheuristic.ai.launchpad.batch.BatchTopLevelService;
import ai.metaheuristic.ai.launchpad.beans.Ids;
import ai.metaheuristic.ai.launchpad.beans.SourceCodeImpl;
import ai.metaheuristic.ai.launchpad.beans.Variable;
import ai.metaheuristic.ai.launchpad.internal_snippet_lib.InternalSnippet;
import ai.metaheuristic.ai.launchpad.internal_snippet_lib.InternalSnippetOutput;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.repositories.IdsRepository;
import ai.metaheuristic.ai.launchpad.repositories.VariableRepository;
import ai.metaheuristic.ai.launchpad.variable.VariableService;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookCache;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.api.launchpad.Workbook;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.metaheuristic.ai.Consts.ZIP_EXT;

/**
 * @author Serge
 * Date: 1/17/2020
 * Time: 9:36 PM
 */
@Service
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class ResourceSplitterSnippet implements InternalSnippet {

    private static final String ATTACHMENTS_POOL_CODE = "attachments";
    private static final Set<String> EXCLUDE_EXT = Set.of(".zip", ".yaml", ".yml");
    private static final String CONFIG_FILE = "config.yaml";
    private static final List<String> EXCLUDE_FROM_MAPPING = List.of("config.yaml", "config.yml");

    private final Globals globals;
    private final PlanCache planCache;
    private final VariableRepository variableRepository;
    private final VariableService variableService;
    private final WorkbookCache workbookCache;
    private final IdsRepository idsRepository;

    public static WorkbookParamsYaml.WorkbookYaml initWorkbookParamsYaml(
            String mainPoolCode, String attachPoolCode, List<String> attachmentCodes) {
        WorkbookParamsYaml.WorkbookYaml wy = new WorkbookParamsYaml.WorkbookYaml();
        wy.preservePoolNames = true;
        wy.poolCodes.computeIfAbsent(Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH, o-> new ArrayList<>()).add(mainPoolCode);
        if (attachmentCodes.isEmpty()) {
            return wy;
        }
        // TODO 2020-01-24 need to re-write with using aliases from sourceCode
        wy.poolCodes.computeIfAbsent(ResourceSplitterSnippet.ATTACHMENTS_POOL_CODE, o-> new ArrayList<>()).add(attachPoolCode);
        return wy;
    }

    public List<InternalSnippetOutput> process(
            Long planId, Long workbookId, String contextId, PlanParamsYaml.VariableDefinition variableDefinition,
            Map<String, List<String>> inputResourceIds) {

        List<String> values = inputResourceIds.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        if (values.size()>1) {
            throw new IllegalStateException("Too many input codes");
        }
        String inputCode = values.get(0);
        Variable bd = variableRepository.findById(Long.valueOf(inputCode)).orElse(null);
        if (bd==null) {
            throw new IllegalStateException("Variable not found for code " + inputCode);
        }
        String originFilename = bd.filename;
        String ext = StrUtils.getExtension(originFilename);

        File tempDir=null;
        try {
            tempDir = DirUtils.createTempDir("batch-file-upload-");
            if (tempDir==null || tempDir.isFile()) {
                String es = "#995.070 can't create temporary directory in " + System.getProperty("java.io.tmpdir");
                throw new IllegalStateException(es);
                // return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#995.070 can't create temporary directory in " + System.getProperty("java.io.tmpdir"));
            }

            final File dataFile = File.createTempFile("uploaded-file-", ext, tempDir);

            if (StringUtils.endsWithIgnoreCase(originFilename, ZIP_EXT)) {

                log.debug("Start unzipping archive");
                Map<String, String> mapping = ZipUtils.unzipFolder(dataFile, tempDir, true, EXCLUDE_FROM_MAPPING);
                log.debug("Start loading file data to db");
                loadFilesFromDirAfterZip(planId, workbookId, contextId, tempDir, mapping);
            }
            else {
                log.debug("Start loading file data to db");
                loadFilesFromDirAfterZip(planId, workbookId, contextId, tempDir, Map.of(dataFile.getName(), originFilename));
            }
        }
        catch(Throwable th) {
            final String es = "#995.110 General processing error.\nError: " + th.getMessage() + ", class: " + th.getClass();
            log.error(es, th);
            ExceptionUtils.rethrow(th);
        }
        finally {
            try {
                if (tempDir!=null) {
                    FileUtils.deleteDirectory(tempDir);
                }
            } catch (IOException e) {
                log.warn("Error deleting dir: {}, error: {}", tempDir.getAbsolutePath(), e.getMessage());
            }
        }
        if (true) {
            throw new NotImplementedException("not yet");
        }
        return null;
    }

    public void loadFilesFromDirAfterZip(Long planId, Long workbookId, String contextId, File srcDir, final Map<String, String> mapping) throws IOException {

        SourceCodeImpl plan = planCache.findById(planId);
        if (plan==null) {
            throw new IllegalStateException("#995.200 sourceCode wasn't found, planId: " + planId);
        }
        Workbook wb = workbookCache.findById(workbookId);
        if (wb==null) {
            throw new IllegalStateException("#995.202 workbook wasn't found, workbookId: " + workbookId);
        }

        if (true) {
            throw new NotImplementedException("need to use real contextId");
        }
        final AtomicBoolean isEmpty = new AtomicBoolean(true);
        Files.list(srcDir.toPath())
                .filter(o -> {
                    File f = o.toFile();
                    return !EXCLUDE_EXT.contains(StrUtils.getExtension(f.getName()));
                })
                .forEach( dataFilePath ->  {
                    isEmpty.set(false);
                    File file = dataFilePath.toFile();
                    String ctxId = contextId+"," + idsRepository.save(new Ids()).id;
                    try {
                        if (file.isDirectory()) {
                            final File mainDocFile = getMainDocumentFileFromConfig(file, mapping);
                            final Stream<BatchTopLevelService.FileWithMapping> files = Files.list(dataFilePath)
                                    .filter(o -> o.toFile().isFile())
                                    .map(f -> {
                                        final String currFileName = file.getName() + File.separatorChar + f.toFile().getName();
                                        final String actualFileName = mapping.get(currFileName);
                                        return new BatchTopLevelService.FileWithMapping(f.toFile(), actualFileName);
                                    });
                            createAndProcessTask(plan, wb, files, mainDocFile, ctxId);
                        } else {
                            String actualFileName = mapping.get(file.getName());
                            createAndProcessTask(plan, wb, Stream.of(new BatchTopLevelService.FileWithMapping(file, actualFileName)), file, ctxId);
                        }
                    } catch (BatchProcessingException | StoreNewFileWithRedirectException e) {
                        throw e;
                    } catch (Throwable th) {
                        String es = "#995.130 An error while saving data to file, " + th.toString();
                        log.error(es, th);
                        throw new BatchResourceProcessingException(es);
                    }
                });
    }

    private void createAndProcessTask(SourceCodeImpl plan, Workbook wb, Stream<BatchTopLevelService.FileWithMapping> dataFiles, File mainDocFile, String contextId) {

        // TODO this method need to be re-written completely
        if (true) {
            throw new NotImplementedException("Need to re-write and use names of variables from snippet config");
        }

/*
        long nanoTime = System.nanoTime();
        List<String> attachments = new ArrayList<>();
        String mainPoolCode = String.format("%d-%s-%d", wb.getId(), Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH, nanoTime);
        String attachPoolCode = String.format("%d-%s-%d", wb.getId(), ATTACHMENTS_POOL_CODE, nanoTime);
        final AtomicBoolean isMainDocPresent = new AtomicBoolean(false);
        AtomicReference<String> mainDocFilename = new AtomicReference<>();
        dataFiles.forEach( fileWithMapping -> {
            String originFilename = fileWithMapping.originName!=null ? fileWithMapping.originName : fileWithMapping.file.getName();
            if (EXCLUDE_EXT.contains(StrUtils.getExtension(originFilename))) {
                return;
            }
            String variable;
            if (fileWithMapping.file.equals(mainDocFile)) {
                variable = mainPoolCode;
                isMainDocPresent.set(true);
                mainDocFilename.set(fileWithMapping.originName);
            }
            else {
                variable = attachPoolCode;
                if (true) {
                    throw new NotImplementedException("Need to re-write and use names of variables from snippet config");
                }
//                attachments.add(code);
            }

            variableService.storeInitialResource(fileWithMapping.file, variable, originFilename, wb.getId(), contextId);
        });

        if (!isMainDocPresent.get()) {
            throw new BatchResourceProcessingException("#995.180 main document wasn't found");
        }

        PlanApiData.TaskProducingResultComplex countTasks = workbookService.produceTasks(false, sourceCode, wb.getId());
        if (countTasks.planProducingStatus != EnumsApi.PlanProducingStatus.OK) {
            workbookService.changeValidStatus(wb.getId(), false);
            throw new BatchResourceProcessingException("#995.220 validation of sourceCode was failed, status: " + countTasks.planValidateStatus);
        }

        if (globals.maxTasksPerWorkbook < countTasks.numberOfTasks) {
            workbookService.changeValidStatus(wb.getId(), false);
            throw new BatchResourceProcessingException(
                    "#995.220 number of tasks for this workbook exceeded the allowed maximum number. Workbook was created but its status is 'not valid'. " +
                            "Allowed maximum number of tasks: " + globals.maxTasksPerWorkbook +", tasks in this workbook:  " + countTasks.numberOfTasks);
        }
        workbookService.changeValidStatus(wb.getId(), true);

        // start producing new tasks
        OperationStatusRest operationStatus = workbookService.workbookTargetExecState(wb.getId(), EnumsApi.WorkbookExecState.PRODUCING);

        if (operationStatus.isErrorMessages()) {
            throw new BatchResourceProcessingException(operationStatus.getErrorMessagesAsStr());
        }
        if (true) {
            throw new NotImplementedException("not yet");
        }
        // TODO 2020-02-05 at this point we have to create new tasks
        //  do we need to invoke produceTasks() ?
        workbookService.produceTasks(true, sourceCode, wb.getId());
//        planService.createAllTasks();

        operationStatus = workbookService.workbookTargetExecState(wb.getId(), EnumsApi.WorkbookExecState.STARTED);

        if (operationStatus.isErrorMessages()) {
            throw new BatchResourceProcessingException(operationStatus.getErrorMessagesAsStr());
        }
*/
    }


    public static File getMainDocumentFileFromConfig(File srcDir, Map<String, String> mapping) throws IOException {
        File configFile = new File(srcDir, CONFIG_FILE);
        if (!configFile.exists()) {
            throw new BatchResourceProcessingException("#995.140 config.yaml file wasn't found in path " + srcDir.getPath());
        }

        if (!configFile.isFile()) {
            throw new BatchResourceProcessingException("#995.150 config.yaml must be a file, not a directory");
        }
        String mainDocumentTemp = getMainDocument(configFile);

        Map.Entry<String, String> entry =
                mapping.entrySet()
                        .stream()
                        .filter(e -> e.getValue().equals(srcDir.getName() + '/' + mainDocumentTemp))
                        .findFirst().orElse(null);

        String mainDocument = entry!=null ? new File(entry.getKey()).getName() : mainDocumentTemp;

        final File mainDocFile = new File(srcDir, mainDocument);
        if (!mainDocFile.exists()) {
            throw new BatchResourceProcessingException("#995.170 main document file "+mainDocument+" wasn't found in path " + srcDir.getPath());
        }
        return mainDocFile;
    }

    private static String getMainDocument(File configFile) throws IOException {
        try (InputStream is = new FileInputStream(configFile)) {
            return getMainDocument(is);
        }
    }

    public static String getMainDocument(InputStream is) {
        String s;
        try {
            s = IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BatchConfigYamlException("#995.153 Can't read config.yaml file, bad content, Error: " + e.getMessage());
        }
        if (!s.contains("mainDocument:")) {
            throw new BatchConfigYamlException("#995.154 Wrong format of config.yaml file, mainDocument field wasn't found");
        }

        // let's try to fix customer's error
        if (s.charAt("mainDocument:".length())!=' ') {
            s = s.replace("mainDocument:", "mainDocument: ");
        }

        Yaml yaml = new Yaml();
        Map<String, Object> config = yaml.load(s);
        String mainDocumentTemp = config.get(Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH).toString();
        if (StringUtils.isBlank(mainDocumentTemp)) {
            throw new BatchResourceProcessingException("#995.160 config.yaml must contain non-empty field '" + Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH + "' ");
        }
        return mainDocumentTemp;
    }


}
