/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.event.events.FindUnassignedTasksAndRegisterInQueueTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionService;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.exceptions.BatchProcessingException;
import ai.metaheuristic.ai.exceptions.BatchResourceProcessingException;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.exceptions.StoreNewFileWithRedirectException;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.stream.Stream;

/**
 * @author Serge
 * Date: 3/25/2021
 * Time: 11:01 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class BatchSplitterTxService {

    private final ExecContextGraphService execContextGraphService;
    private final InternalFunctionService internalFunctionService;
    private final TaskProducingService taskProducingService;
    private final VariableTxService variableService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_UNCOMMITTED)
    public void loadFilesFromDirAfterZip(ExecContextData.SimpleExecContext simpleExecContext, Path srcDir,
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
                taskParamsYaml.task.taskContextId, executionContextData.subProcesses.get(0).processContextId);

        ExecContextData.GraphAndStates graphAndStates = execContextGraphService.prepareGraphAndStates(simpleExecContext.execContextGraphId, simpleExecContext.execContextTaskStateId);

        try {
            // do not remove try(Stream<Path>){}
            try (final Stream<Path> list = Files.list(srcDir)) {
                list
                        .forEach(dataFilePath -> {
                            currTaskNumber.incrementAndGet();
                            try {
                                VariableData.VariableDataSource variableDataSource = getVariableDataSource(mapping, dataFilePath, dataFilePath);
                                if (variableDataSource == null) {
                                    return;
                                }
                                String currTaskContextId = ContextUtils.buildTaskContextId(subProcessContextId, Integer.toString(currTaskNumber.get()));
                                variableService.createInputVariablesForSubProcess(
                                        variableDataSource, simpleExecContext.execContextId, variableName, currTaskContextId, true);

                                taskProducingService.createTasksForSubProcesses(graphAndStates, simpleExecContext, executionContextData, currTaskContextId, taskId, lastIds);

                            }
                            catch (BatchProcessingException | StoreNewFileWithRedirectException e) {
                                throw e;
                            }
                            catch (Throwable th) {
                                String es = "#995.300 An error while saving data to file, " + th.getMessage();
                                log.error(es, th);
                                throw new BatchResourceProcessingException(es);
                            }
                        });
            }
        } catch (IOException e) {
            String es = "#995.310 An error while saving data to file, " + e;
            log.error(es, e);
            throw new BatchResourceProcessingException(es);
        }
        execContextGraphService.createEdges(graphAndStates.graph(), lastIds, executionContextData.descendants);

        eventPublisher.publishEvent(new FindUnassignedTasksAndRegisterInQueueTxEvent());
    }

    @Nullable
    private static VariableData.VariableDataSource getVariableDataSource(Map<String, String> mapping, Path dataFilePath, Path file) throws IOException {
        VariableData.VariableDataSource variableDataSource;
        if (Files.isDirectory(file)) {
            final List<BatchTopLevelService.FileWithMapping> files;
            // do not remove try(Stream<Path>){}
            try (final Stream<Path> stream = Files.list(dataFilePath)) {
                files = stream
                        .filter(o -> o.toFile().isFile())
                        .map(f -> {
                            final String currFileName = file.getFileName().toString() + File.separatorChar + f.getFileName().toString();
                            final String actualFileName = mapping.get(currFileName);
                            return new BatchTopLevelService.FileWithMapping(f, actualFileName);
                        }).collect(Collectors.toList());
            }
            if (files.isEmpty()) {
                log.error("#995.290 there isn't any files in dir {}", file.toAbsolutePath());
                return null;
            }
            variableDataSource = new VariableData.VariableDataSource(files);
        } else {
            variableDataSource = new VariableData.VariableDataSource(
                    List.of(new BatchTopLevelService.FileWithMapping(file, mapping.get(file.getFileName().toString()))));
        }
        return variableDataSource;
    }


}

