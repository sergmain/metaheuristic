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

package ai.metaheuristic.ai.dispatcher.internal_functions.batch_splitter;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.exceptions.BatchProcessingException;
import ai.metaheuristic.ai.exceptions.BatchResourceProcessingException;
import ai.metaheuristic.ai.exceptions.StoreNewFileWithRedirectException;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.UnzipArchiveException;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static ai.metaheuristic.ai.Consts.INTERNAL_FUNCTION_PROCESSING_RESULT_OK;
import static ai.metaheuristic.ai.Consts.ZIP_EXT;
import static ai.metaheuristic.ai.dispatcher.data.InternalFunctionData.InternalFunctionProcessingResult;

/**
 * @author Serge
 * Date: 1/17/2020
 * Time: 9:36 PM
 */
@SuppressWarnings("DuplicatedCode")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class BatchSplitterFunction implements InternalFunction {

    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final InternalFunctionVariableService internalFunctionVariableService;
    private final InternalFunctionService internalFunctionService;

    @Override
    public String getCode() {
        return Consts.MH_BATCH_SPLITTER_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_BATCH_SPLITTER_FUNCTION;
    }

    public InternalFunctionProcessingResult process(
            @NonNull Long sourceCodeId, @NonNull Long execContextId, @NonNull Long taskId, @NonNull String taskContextId,
            @NonNull ExecContextParamsYaml.VariableDeclaration variableDeclaration,
            @NonNull TaskParamsYaml taskParamsYaml) {

        // variable-for-splitting
        String inputVariableName = MetaUtils.getValue(taskParamsYaml.task.metas, "variable-for-splitting");
        if (S.b(inputVariableName)) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found, "#995.020 Meta 'variable-for-splitting' wasn't found");
        }

        List<VariableUtils.VariableHolder> holders = new ArrayList<>();
        InternalFunctionProcessingResult result = internalFunctionVariableService.discoverVariables(execContextId, taskContextId, inputVariableName, holders);
        if (result != null) {
            return result;
        }
        if (holders.size()>1) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, "#995.040 Too many variables");
        }

        VariableUtils.VariableHolder variableHolder = holders.get(0);
        String originFilename = variableHolder.getFilename();
        if (S.b(originFilename)) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.input_variable_isnt_file, "#995.060 variable.filename is blank");
        }

        String ext = StrUtils.getExtension(originFilename);

        File tempDir=DirUtils.createTempDir("batch-file-upload-");
        try {
            if (tempDir==null || tempDir.isFile()) {
                String es = "#995.080 can't create temporary directory in " + System.getProperty("java.io.tmpdir");
                log.error(es);
                return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es);
            }

            final File dataFile = File.createTempFile("uploaded-file-", ext, tempDir);
            internalFunctionVariableService.storeToFile(variableHolder, dataFile);
            if (dataFile.length()==0) {
                return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, "#995.100 Empty files aren't supported");
            }

            if (StringUtils.endsWithIgnoreCase(originFilename, ZIP_EXT)) {
                log.debug("Start unzipping archive");
                File zipDir=new File(tempDir, "zip");

                Map<String, String> mapping = ZipUtils.unzipFolder(dataFile, zipDir, true, List.of());
                log.debug("Start loading .zip file data to db");
                return loadFilesFromDirAfterZip(sourceCodeId, execContextId, zipDir, mapping, taskParamsYaml, taskId);
            }
            else {
                log.debug("Start loading file data to db");
                return loadFilesFromDirAfterZip(sourceCodeId, execContextId, tempDir, Map.of(dataFile.getName(), originFilename), taskParamsYaml, taskId);
            }
        }
        catch(UnzipArchiveException e) {
            final String es = "#995.120 can't unzip an archive. Error: " + e.getMessage() + ", class: " + e.getClass();
            log.error(es, e);
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es);
        }
        catch(BatchProcessingException e) {
            final String es = "#995.140 General error of processing batch.\nError: " + e.getMessage();
            log.error(es, e);
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es);
        }
        catch(Throwable th) {
            final String es = "#995.160 General processing error.\nError: " + th.getMessage() + ", class: " + th.getClass();
            log.error(es, th);
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es);
        }
        finally {
            try {
                if (tempDir!=null && tempDir.exists()) {
                    FileUtils.deleteDirectory(tempDir);
                }
            } catch (IOException e) {
                log.warn("#995.180 Error deleting dir: {}, error: {}", tempDir.getAbsolutePath(), e.getMessage());
            }
        }
    }

    private InternalFunctionProcessingResult loadFilesFromDirAfterZip(
            Long sourceCodeId, Long execContextId, File srcDir,
            final Map<String, String> mapping, TaskParamsYaml taskParamsYaml, Long taskId) throws IOException {

        InternalFunctionData.ExecutionContextData executionContextData = internalFunctionService.getSupProcesses(sourceCodeId, execContextId, taskParamsYaml, taskId);
        if (executionContextData.internalFunctionProcessingResult.processing!= Enums.InternalFunctionProcessing.ok) {
            return executionContextData.internalFunctionProcessingResult;
        }

        final String variableName = MetaUtils.getValue(executionContextData.process.metas, "output-variable");
        if (S.b(variableName)) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_is_broken,
                    "#995.280 Meta with key 'output-variable' wasn't found for process '"+executionContextData.process.processCode+"'");
        }

        final List<Long> lastIds = new ArrayList<>();
        AtomicInteger currTaskNumber = new AtomicInteger(0);
        Files.list(srcDir.toPath())
                .forEach( dataFilePath ->  {
                    File file = dataFilePath.toFile();
                    currTaskNumber.incrementAndGet();
                    try {
                        if (file.isDirectory()) {
                            final Stream<BatchTopLevelService.FileWithMapping> files = Files.list(dataFilePath)
                                    .filter(o -> o.toFile().isFile())
                                    .map(f -> {
                                        final String currFileName = file.getName() + File.separatorChar + f.toFile().getName();
                                        final String actualFileName = mapping.get(currFileName);
                                        return new BatchTopLevelService.FileWithMapping(f.toFile(), actualFileName);
                                    });
                            internalFunctionService.createTasksForSubProcesses(
                                    files, null,
                                    execContextId, executionContextData, currTaskNumber, taskId, variableName, lastIds
                            );
                        } else {
                            String actualFileName = mapping.get(file.getName());
                            internalFunctionService.createTasksForSubProcesses(
                                    Stream.of(new BatchTopLevelService.FileWithMapping(file, actualFileName)), null,
                                    execContextId, executionContextData, currTaskNumber, taskId, variableName, lastIds
                            );
                        }
                    } catch (BatchProcessingException | StoreNewFileWithRedirectException e) {
                        throw e;
                    } catch (Throwable th) {
                        String es = "#995.300 An error while saving data to file, " + th.toString();
                        log.error(es, th);
                        throw new BatchResourceProcessingException(es);
                    }
                });
        execContextGraphTopLevelService.createEdges(execContextId, lastIds, executionContextData.descendants);
        return INTERNAL_FUNCTION_PROCESSING_RESULT_OK;
    }

}
