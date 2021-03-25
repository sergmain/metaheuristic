/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.exceptions.BatchProcessingException;
import ai.metaheuristic.ai.exceptions.BatchResourceProcessingException;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.exceptions.StoreNewFileWithRedirectException;
import ai.metaheuristic.ai.utils.ContextUtils;
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
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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

    private final ExecContextGraphService execContextGraphService;
    private final InternalFunctionVariableService internalFunctionVariableService;
    private final InternalFunctionService internalFunctionService;
    private final TaskProducingService taskProducingService;
    private final ExecContextSyncService execContextSyncService;
    private final VariableService variableService;

    @Override
    public String getCode() {
        return Consts.MH_BATCH_SPLITTER_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_BATCH_SPLITTER_FUNCTION;
    }

    @Override
    public void process(
            ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
            TaskParamsYaml taskParamsYaml) {

        // variable-for-splitting
        String inputVariableName = MetaUtils.getValue(taskParamsYaml.task.metas, "variable-for-splitting");
        if (S.b(inputVariableName)) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found, "#995.020 Meta 'variable-for-splitting' wasn't found"));
        }

        List<VariableUtils.VariableHolder> holders = internalFunctionVariableService.discoverVariables(simpleExecContext.execContextId, taskContextId, inputVariableName);
        if (holders.size()>1) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, "#995.040 Too many variables"));
        }

        VariableUtils.VariableHolder variableHolder = holders.get(0);
        String originFilename = variableHolder.getFilename();
        if (S.b(originFilename)) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.input_variable_isnt_file, "#995.060 variable.filename is blank"));
        }

        String ext = StrUtils.getExtension(originFilename);

        File tempDir=DirUtils.createTempDir("batch-file-upload-");
        try {
            if (tempDir==null || tempDir.isFile()) {
                String es = "#995.080 can't create temporary directory in " + System.getProperty("java.io.tmpdir");
                log.error(es);
                throw new InternalFunctionException(
                    new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es));
            }

            final File dataFile = File.createTempFile("uploaded-file-", ext, tempDir);
            internalFunctionVariableService.storeToFile(variableHolder, dataFile);
            if (dataFile.length()==0) {
                throw new InternalFunctionException(
                    new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, "#995.100 Empty files aren't supported"));
            }

            if (StringUtils.endsWithIgnoreCase(originFilename, ZIP_EXT)) {
                log.debug("Start unzipping archive");
                File zipDir=new File(tempDir, "zip");

                Map<String, String> mapping = ZipUtils.unzipFolder(dataFile, zipDir, true, List.of());
                log.debug("Start loading .zip file data to db");
                loadFilesFromDirAfterZip(simpleExecContext, zipDir, mapping, taskParamsYaml, taskId);
            }
            else {
                log.debug("Start loading file data to db");
                loadFilesFromDirAfterZip(simpleExecContext, tempDir, Map.of(dataFile.getName(), originFilename), taskParamsYaml, taskId);
            }
        }
        catch(UnzipArchiveException e) {
            final String es = "#995.120 can't unzip an archive. Error: " + e.getMessage() + ", class: " + e.getClass();
            log.error(es, e);
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es));
        }
        catch(BatchProcessingException e) {
            final String es = "#995.140 General error of processing batch.\nError: " + e.getMessage();
            log.error(es, e);
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es));
        }
        catch(Throwable th) {
            final String es = "#995.160 General processing error.\nError: " + th.getMessage() + ", class: " + th.getClass();
            log.error(es, th);
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es));
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

    private void loadFilesFromDirAfterZip(ExecContextData.SimpleExecContext simpleExecContext, File srcDir,
            final Map<String, String> mapping, TaskParamsYaml taskParamsYaml, Long taskId) throws IOException {

        InternalFunctionData.ExecutionContextData executionContextData = internalFunctionService.getSubProcesses(simpleExecContext, taskParamsYaml, taskId);
        if (executionContextData.internalFunctionProcessingResult.processing!= Enums.InternalFunctionProcessing.ok) {
            throw new InternalFunctionException(executionContextData.internalFunctionProcessingResult);
        }

        if (executionContextData.subProcesses.isEmpty()) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.sub_process_not_found,
                    "#995.275 there isn't any sub-process for process '"+executionContextData.process.processCode+"'"));
        }

        final String variableName = MetaUtils.getValue(executionContextData.process.metas, "output-variable");
        if (S.b(variableName)) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_is_broken,
                    "#995.280 Meta with key 'output-variable' wasn't found for process '"+executionContextData.process.processCode+"'"));
        }

        final List<Long> lastIds = new ArrayList<>();
        AtomicInteger currTaskNumber = new AtomicInteger(0);
        String subProcessContextId = ContextUtils.getCurrTaskContextIdForSubProcesses(
                taskId, taskParamsYaml.task.taskContextId, executionContextData.subProcesses.get(0).processContextId);

        Files.list(srcDir.toPath())
                .forEach( dataFilePath ->  {
                    File file = dataFilePath.toFile();
                    currTaskNumber.incrementAndGet();
                    try {
                        VariableData.VariableDataSource variableDataSource = getVariableDataSource(mapping, dataFilePath, file);
                        if (variableDataSource == null) {
                            return;
                        }
                        String currTaskContextId = ContextUtils.getTaskContextId(subProcessContextId, Integer.toString(currTaskNumber.get()));
                        variableService.createInputVariablesForSubProcess(
                                variableDataSource, simpleExecContext.execContextId, variableName, currTaskContextId);

                        taskProducingService.createTasksForSubProcesses(
                                simpleExecContext, executionContextData, currTaskContextId, taskId, lastIds);

                    } catch (BatchProcessingException | StoreNewFileWithRedirectException e) {
                        throw e;
                    } catch (Throwable th) {
                        String es = "#995.300 An error while saving data to file, " + th.toString();
                        log.error(es, th);
                        throw new BatchResourceProcessingException(es);
                    }
                });
        execContextGraphService.createEdges(simpleExecContext.execContextGraphId, lastIds, executionContextData.descendants);
    }

    @Nullable
    private VariableData.VariableDataSource getVariableDataSource(Map<String, String> mapping, Path dataFilePath, File file) throws IOException {
        VariableData.VariableDataSource variableDataSource;
        if (file.isDirectory()) {
            final List<BatchTopLevelService.FileWithMapping> files = Files.list(dataFilePath)
                    .filter(o -> o.toFile().isFile())
                    .map(f -> {
                        final String currFileName = file.getName() + File.separatorChar + f.toFile().getName();
                        final String actualFileName = mapping.get(currFileName);
                        return new BatchTopLevelService.FileWithMapping(f.toFile(), actualFileName);
                    }).collect(Collectors.toList());

            if (files.isEmpty()) {
                log.error("#995.290 there isn't any files in dir {}", file.getAbsolutePath());
                return null;
            }
            variableDataSource = new VariableData.VariableDataSource(files);
        } else {
            variableDataSource = new VariableData.VariableDataSource(
                    List.of(new BatchTopLevelService.FileWithMapping(file, mapping.get(file.getName()))));
        }
        return variableDataSource;
    }

}
