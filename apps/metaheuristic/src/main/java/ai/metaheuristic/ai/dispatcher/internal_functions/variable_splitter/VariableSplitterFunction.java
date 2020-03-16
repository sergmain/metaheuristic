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

package ai.metaheuristic.ai.dispatcher.internal_functions.variable_splitter;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.beans.Ids;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionProcessor;
import ai.metaheuristic.ai.dispatcher.repositories.IdsRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.BatchConfigYamlException;
import ai.metaheuristic.ai.exceptions.BatchProcessingException;
import ai.metaheuristic.ai.exceptions.BatchResourceProcessingException;
import ai.metaheuristic.ai.exceptions.StoreNewFileWithRedirectException;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.commons.S;
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

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.metaheuristic.ai.Consts.ZIP_EXT;
import static ai.metaheuristic.ai.dispatcher.data.InternalFunctionData.InternalFunctionProcessingResult;

/**
 * @author Serge
 * Date: 1/17/2020
 * Time: 9:36 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class VariableSplitterFunction implements InternalFunction {

    private static final String ATTACHMENTS_POOL_CODE = "attachments";
    private static final Set<String> EXCLUDE_EXT = Set.of(".zip", ".yaml", ".yml");
    private static final String CONFIG_FILE = "config.yaml";
    private static final List<String> EXCLUDE_FROM_MAPPING = List.of("config.yaml", "config.yml");

    private final Globals globals;
    private final SourceCodeCache sourceCodeCache;
    private final VariableRepository variableRepository;
    private final VariableService variableService;
    private final ExecContextCache execContextCache;
    private final IdsRepository idsRepository;

    @Override
    public String getCode() {
        return Consts.MH_VARIABLE_SPLITTER_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_VARIABLE_SPLITTER_FUNCTION;
    }

    public InternalFunctionProcessingResult process(
            Long sourceCodeId, Long execContextId, String internalContextId, SourceCodeParamsYaml.VariableDefinition variableDefinition,
            Map<String, List<String>> inputResourceIds) {

        List<String> values = inputResourceIds.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        if (values.size()>1) {
            throw new IllegalStateException("Too many input codes");
        }
        String inputCode = values.get(0);
        Variable variable = variableRepository.findById(Long.valueOf(inputCode)).orElse(null);
        if (variable==null) {
            throw new IllegalStateException("Variable not found for code " + inputCode);
        }
        String originFilename = variable.filename;
        if (S.b(originFilename)) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_is_broken, "variable.filename is blank");
        }

        String ext = StrUtils.getExtension(originFilename);

        File tempDir=null;
        try {
            tempDir = DirUtils.createTempDir("batch-file-upload-");
            if (tempDir==null || tempDir.isFile()) {
                String es = "#995.070 can't create temporary directory in " + System.getProperty("java.io.tmpdir");
                log.error(es);
                return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es);
            }

            final File dataFile = File.createTempFile("uploaded-file-", ext, tempDir);

            if (StringUtils.endsWithIgnoreCase(originFilename, ZIP_EXT)) {
                log.debug("Start unzipping archive");
                Map<String, String> mapping = ZipUtils.unzipFolder(dataFile, tempDir, true, EXCLUDE_FROM_MAPPING);
                log.debug("Start loading file data to db");
                loadFilesFromDirAfterZip(sourceCodeId, execContextId, internalContextId, tempDir, mapping);
            }
            else {
                log.debug("Start loading file data to db");
                loadFilesFromDirAfterZip(sourceCodeId, execContextId, internalContextId, tempDir, Map.of(dataFile.getName(), originFilename));
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
        return Consts.INTERNAL_FUNCTION_PROCESSING_RESULT_OK;
    }

    public void loadFilesFromDirAfterZip(Long sourceCodeId, Long execContextId, String internalContextId, File srcDir, final Map<String, String> mapping) throws IOException {

        SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode==null) {
            throw new IllegalStateException("#995.200 sourceCode wasn't found, sourceCodeId: " + sourceCodeId);
        }
        ExecContext wb = execContextCache.findById(execContextId);
        if (wb==null) {
            throw new IllegalStateException("#995.202 execContext wasn't found, execContextId: " + execContextId);
        }

        if (true) {
            throw new NotImplementedException("need to use real internalContextId");
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
                    String ctxId = internalContextId+"," + idsRepository.save(new Ids()).id;
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
                            createAndProcessTask(sourceCode, wb, files, mainDocFile, ctxId);
                        } else {
                            String actualFileName = mapping.get(file.getName());
                            createAndProcessTask(sourceCode, wb, Stream.of(new BatchTopLevelService.FileWithMapping(file, actualFileName)), file, ctxId);
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

    private void createAndProcessTask(SourceCodeImpl sourceCode, ExecContext ec, Stream<BatchTopLevelService.FileWithMapping> dataFiles, File mainDocFile, String contextId) {

        // TODO this method need to be re-written completely
        if (true) {
            throw new NotImplementedException("Need to re-write and use names of variables from function config");
        }

/*
        long nanoTime = System.nanoTime();
        List<String> attachments = new ArrayList<>();
        String mainPoolCode = String.format("%d-%s-%d", ec.getId(), Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH, nanoTime);
        String attachPoolCode = String.format("%d-%s-%d", ec.getId(), ATTACHMENTS_POOL_CODE, nanoTime);
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
                    throw new NotImplementedException("Need to re-write and use names of variables from function config");
                }
//                attachments.add(code);
            }

            variableService.storeInitialResource(fileWithMapping.file, variable, originFilename, ec.getId(), contextId);
        });

        if (!isMainDocPresent.get()) {
            throw new BatchResourceProcessingException("#995.180 main document wasn't found");
        }

        SourceCodeApiData.TaskProducingResultComplex countTasks = execContextService.produceTasks(false, sourceCode, ec.getId());
        if (countTasks.sourceCodeProducingStatus != EnumsApi.SourceCodeProducingStatus.OK) {
            execContextService.changeValidStatus(ec.getId(), false);
            throw new BatchResourceProcessingException("#995.220 validation of sourceCode was failed, status: " + countTasks.sourceCodeValidateStatus);
        }

        if (globals.maxTasksPerExecContext < countTasks.numberOfTasks) {
            execContextService.changeValidStatus(ec.getId(), false);
            throw new BatchResourceProcessingException(
                    "#995.220 number of tasks for this execContext exceeded the allowed maximum number. ExecContext was created but its status is 'not valid'. " +
                            "Allowed maximum number of tasks: " + globals.maxTasksPerExecContext +", tasks in this execContext: " + countTasks.numberOfTasks);
        }
        execContextService.changeValidStatus(ec.getId(), true);

        // start producing new tasks
        OperationStatusRest operationStatus = execContextService.execContextTargetExecState(ec.getId(), EnumsApi.ExecContextState.PRODUCING);

        if (operationStatus.isErrorMessages()) {
            throw new BatchResourceProcessingException(operationStatus.getErrorMessagesAsStr());
        }
        if (true) {
            throw new NotImplementedException("not yet");
        }
        // TODO 2020-02-05 at this point we have to create new tasks
        //  do we need to invoke produceTasks() ?
        execContextService.produceTasks(true, sourceCode, ec.getId());
//        sourceCodeService.createAllTasks();

        operationStatus = execContextService.execContextTargetExecState(ec.getId(), EnumsApi.ExecContextState.STARTED);

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
