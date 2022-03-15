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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionService;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.BatchProcessingException;
import ai.metaheuristic.ai.exceptions.BatchResourceProcessingException;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.exceptions.StoreNewFileWithRedirectException;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 3/25/2021
 * Time: 11:01 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class BatchSplitterTxService {

    private final ExecContextGraphService execContextGraphService;
    private final InternalFunctionService internalFunctionService;
    private final TaskProducingService taskProducingService;
    private final VariableService variableService;

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_UNCOMMITTED)
    public void loadFilesFromDirAfterZip(ExecContextData.SimpleExecContext simpleExecContext, File srcDir,
                                         final Map<String, String> mapping, TaskParamsYaml taskParamsYaml, Long taskId) {

        InternalFunctionData.ExecutionContextData executionContextData = internalFunctionService.getSubProcesses(simpleExecContext, taskParamsYaml, taskId);
        if (executionContextData.internalFunctionProcessingResult.processing!= Enums.InternalFunctionProcessing.ok) {
            throw new InternalFunctionException(executionContextData.internalFunctionProcessingResult);
        }

        if (executionContextData.subProcesses.isEmpty()) {
            throw new InternalFunctionException(
                    new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.sub_process_not_found,
                            "#995.275 there isn't any sub-process for process '"+executionContextData.process.processCode+"'"));
        }

        final String variableName = MetaUtils.getValue(executionContextData.process.metas, "output-variable");
        if (S.b(variableName)) {
            throw new InternalFunctionException(
                    new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_is_broken,
                            "#995.280 Meta with key 'output-variable' wasn't found for process '"+executionContextData.process.processCode+"'"));
        }

        final List<Long> lastIds = new ArrayList<>();
        AtomicInteger currTaskNumber = new AtomicInteger(0);
        String subProcessContextId = ContextUtils.getCurrTaskContextIdForSubProcesses(
                taskId, taskParamsYaml.task.taskContextId, executionContextData.subProcesses.get(0).processContextId);

        try {
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
                                    variableDataSource, simpleExecContext.execContextId, variableName, currTaskContextId, true);

                            taskProducingService.createTasksForSubProcesses(
                                    simpleExecContext, executionContextData, currTaskContextId, taskId, lastIds);

                        } catch (BatchProcessingException | StoreNewFileWithRedirectException e) {
                            throw e;
                        } catch (Throwable th) {
                            String es = "#995.300 An error while saving data to file, " + th.getMessage();
                            log.error(es, th);
                            throw new BatchResourceProcessingException(es);
                        }
                    });
        } catch (IOException e) {
            String es = "#995.310 An error while saving data to file, " + e.getMessage();
            log.error(es, e);
            throw new BatchResourceProcessingException(es);
        }
        execContextGraphService.createEdges(simpleExecContext.execContextGraphId, lastIds, executionContextData.descendants);
    }

    @Nullable
    private static VariableData.VariableDataSource getVariableDataSource(Map<String, String> mapping, Path dataFilePath, File file) throws IOException {
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

