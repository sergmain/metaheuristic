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
import ai.metaheuristic.ai.exceptions.BatchConfigYamlException;
import ai.metaheuristic.ai.exceptions.BatchProcessingException;
import ai.metaheuristic.ai.exceptions.BatchResourceProcessingException;
import ai.metaheuristic.ai.exceptions.StoreNewFileWithRedirectException;
import ai.metaheuristic.ai.launchpad.batch.BatchService;
import ai.metaheuristic.ai.launchpad.batch.BatchTopLevelService;
import ai.metaheuristic.ai.launchpad.beans.Batch;
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.launchpad.plan.PlanUtils;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.plan.PlanApiData;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.commons.exceptions.UnzipArchiveException;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
public class ResourceSplitterSnippet {

    private static final Set<String> EXCLUDE_EXT = Set.of(".zip", ".yaml", ".yml");
    private static final String CONFIG_FILE = "config.yaml";

    public final BatchService batchService;

    public void process(List<String> poolResourceCodes) {

        log.info("The file {} was successfully stored to disk", originFilename);
        new Thread(() -> {
            try {
                if (StringUtils.endsWithIgnoreCase(originFilename, ZIP_EXT)) {

                    log.debug("Start unzipping archive");
                    Map<String, String> mapping = ZipUtils.unzipFolder(dataFile, tempDir, true, EXCLUDE_FROM_MAPPING);
                    log.debug("Start loading file data to db");
                    loadFilesFromDirAfterZip(batch, tempDir, mapping);
                }
                else {
                    log.debug("Start loading file data to db");
                    loadFilesFromDirAfterZip(batch, tempDir, Map.of(dataFile.getName(), originFilename));
                }
                batchService.changeStateToProcessing(batch.id);
            }
            catch(UnzipArchiveException e) {
                final String es = "#995.100 can't unzip an archive. Error: " + e.getMessage() + ", class: " + e.getClass();
                log.error(es, e);
                batchService.changeStateToError(batch.id, es);
            }
            catch(BatchProcessingException e) {
                final String es = "#995.105 General error of processing batch.\nError: " + e.getMessage();
                log.error(es, e);
                batchService.changeStateToError(batch.id, es);
            }
            catch(Throwable th) {
                final String es = "#995.110 General processing error.\nError: " + th.getMessage() + ", class: " + th.getClass();
                log.error(es, th);
                batchService.changeStateToError(batch.id, es);
            }
            finally {
                try {
                    FileUtils.deleteDirectory(tempDir);
                } catch (IOException e) {
                    log.warn("Error deleting dir: {}, error: {}", tempDir.getAbsolutePath(), e.getMessage());
                }
            }
        }).start();

    }

    public void loadFilesFromDirAfterZip(Batch batch, File srcDir, final Map<String, String> mapping) throws IOException {

        final AtomicBoolean isEmpty = new AtomicBoolean(true);
        Files.list(srcDir.toPath())
                .filter(o -> {
                    File f = o.toFile();
                    return !EXCLUDE_EXT.contains(StrUtils.getExtension(f.getName()));
                })
                .forEach( dataFilePath ->  {
                    isEmpty.set(false);
                    File file = dataFilePath.toFile();
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
                            createAndProcessTask(batch, files, mainDocFile);
                        } else {
                            String actualFileName = mapping.get(file.getName());
                            createAndProcessTask(batch, Stream.of(new BatchTopLevelService.FileWithMapping(file, actualFileName)), file);
                        }
                    } catch (BatchProcessingException | StoreNewFileWithRedirectException e) {
                        throw e;
                    } catch (Throwable th) {
                        String es = "#995.130 An error while saving data to file, " + th.toString();
                        log.error(es, th);
                        throw new BatchResourceProcessingException(es);
                    }
                });


        if (isEmpty.get()) {
            changeStateToFinished(batch.id);
        }

    }

    private void createAndProcessTask(Batch batch, Stream<BatchTopLevelService.FileWithMapping> dataFiles, File mainDocFile) {

        Long planId = batch.planId;
        long nanoTime = System.nanoTime();
        List<String> attachments = new ArrayList<>();
        String mainPoolCode = String.format("%d-%s-%d", planId, Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH, nanoTime);
        String attachPoolCode = String.format("%d-%s-%d", planId, ATTACHMENTS_POOL_CODE, nanoTime);
        final AtomicBoolean isMainDocPresent = new AtomicBoolean(false);
        AtomicReference<String> mainDocFilename = new AtomicReference<>();
        dataFiles.forEach( fileWithMapping -> {
            String originFilename = fileWithMapping.originName!=null ? fileWithMapping.originName : fileWithMapping.file.getName();
            if (EXCLUDE_EXT.contains(StrUtils.getExtension(originFilename))) {
                return;
            }
            final String code = ResourceUtils.toResourceCode(fileWithMapping.file.getName());

            String poolCode;
            if (fileWithMapping.file.equals(mainDocFile)) {
                poolCode = mainPoolCode;
                isMainDocPresent.set(true);
                mainDocFilename.set(fileWithMapping.originName);
            }
            else {
                poolCode = attachPoolCode;
                attachments.add(code);
            }

            resourceService.storeInitialResource(fileWithMapping.file, code, poolCode, originFilename);
        });

        if (!isMainDocPresent.get()) {
            throw new BatchResourceProcessingException("#995.180 main document wasn't found");
        }

        final WorkbookParamsYaml.WorkbookYaml params = PlanUtils.initWorkbookParamsYaml(mainPoolCode, attachPoolCode, attachments);
        PlanApiData.TaskProducingResultComplex producingResult = workbookService.createWorkbook(planId, params);
        if (producingResult.planProducingStatus!= EnumsApi.PlanProducingStatus.OK) {
            throw new BatchResourceProcessingException("#995.190 Error creating workbook: " + producingResult.planProducingStatus);
        }

        PlanImpl plan = planCache.findById(planId);
        if (plan == null) {
            throw new BatchResourceProcessingException("#995.200 plan wasn't found, planId: " + planId);
        }

        PlanApiData.TaskProducingResultComplex countTasks = workbookService.produceTasks(false, plan, producingResult.workbook.getId());
        if (countTasks.planProducingStatus != EnumsApi.PlanProducingStatus.OK) {
            workbookService.changeValidStatus(batch.workbookId, false);
            throw new BatchResourceProcessingException("#995.220 validation of plan was failed, status: " + countTasks.planValidateStatus);
        }

        if (globals.maxTasksPerWorkbook < countTasks.numberOfTasks) {
            workbookService.changeValidStatus(producingResult.workbook.getId(), false);
            throw new BatchResourceProcessingException(
                    "#995.220 number of tasks for this workbook exceeded the allowed maximum number. Workbook was created but its status is 'not valid'. " +
                            "Allowed maximum number of tasks: " + globals.maxTasksPerWorkbook +", tasks in this workbook:  " + countTasks.numberOfTasks);
        }
        workbookService.changeValidStatus(producingResult.workbook.getId(), true);

        // start producing new tasks
        OperationStatusRest operationStatus = planService.workbookTargetExecState(producingResult.workbook.getId(), EnumsApi.WorkbookExecState.PRODUCING);

        if (operationStatus.isErrorMessages()) {
            throw new BatchResourceProcessingException(operationStatus.getErrorMessagesAsStr());
        }
        planService.createAllTasks();
        operationStatus = planService.workbookTargetExecState(producingResult.workbook.getId(), EnumsApi.WorkbookExecState.STARTED);

        if (operationStatus.isErrorMessages()) {
            throw new BatchResourceProcessingException(operationStatus.getErrorMessagesAsStr());
        }
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
