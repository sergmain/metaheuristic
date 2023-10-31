/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.internal_functions.batch_line_splitter;

import ai.metaheuristic.ai.Enums;
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
import ai.metaheuristic.commons.exceptions.UnzipArchiveException;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Serge
 * Date: 3/24/2021
 * Time: 11:28 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class BatchLineSplitterTxService {

    private final VariableTxService variableService;
    private final InternalFunctionService internalFunctionService;
    private final TaskProducingService taskProducingService;
    private final ExecContextGraphService execContextGraphService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_UNCOMMITTED)
    public Void createTasksTx(ExecContextData.SimpleExecContext simpleExecContext, Long taskId, TaskParamsYaml taskParamsYaml, Long numberOfLines, String content) {
        try {
            ExecContextData.GraphAndStates graphAndStates = execContextGraphService.prepareGraphAndStates(simpleExecContext.execContextGraphId, simpleExecContext.execContextTaskStateId);
            createTasks(simpleExecContext, graphAndStates, content, taskParamsYaml, taskId, numberOfLines);
            execContextGraphService.save(graphAndStates);
        }
        catch (InternalFunctionException e) {
            throw e;
        }
        catch(UnzipArchiveException e) {
            final String es = "#994.120 can't unzip an archive. Error: " + e.getMessage() + ", class: " + e.getClass();
            log.error(es, e);
            throw new InternalFunctionException(Enums.InternalFunctionProcessing.system_error, es);
        }
        catch(BatchProcessingException e) {
            final String es = "#994.140 General error of processing batch.\nError: " + e.getMessage();
            log.error(es, e);
            throw new InternalFunctionException(Enums.InternalFunctionProcessing.system_error, es);
        }
        catch(Throwable th) {
            final String es = "#994.160 General processing error.\nError: " + th.getMessage() + ", class: " + th.getClass();
            log.error(es, th);
            throw new InternalFunctionException(Enums.InternalFunctionProcessing.system_error, es);
        }
        eventPublisher.publishEvent(new FindUnassignedTasksAndRegisterInQueueTxEvent());
        return null;
    }

    private void createTasks(ExecContextData.SimpleExecContext simpleExecContext, ExecContextData.GraphAndStates graphAndStates, String content, TaskParamsYaml taskParamsYaml, Long taskId, Long numberOfLines) {

        InternalFunctionData.ExecutionContextData executionContextData = internalFunctionService.getSubProcesses(simpleExecContext, taskParamsYaml, taskId);
        if (executionContextData.internalFunctionProcessingResult.processing!= Enums.InternalFunctionProcessing.ok) {
            throw new InternalFunctionException(executionContextData.internalFunctionProcessingResult);
        }

        if (executionContextData.subProcesses.isEmpty()) {
            throw new InternalFunctionException(Enums.InternalFunctionProcessing.sub_process_not_found,
                    "#994.275 there isn't any sub-process for process '"+executionContextData.process.processCode+"'");
        }

        final String variableName = MetaUtils.getValue(executionContextData.process.metas, BatchLineSplitterFunction.OUTPUT_VARIABLE);
        if (S.b(variableName)) {
            throw new InternalFunctionException(Enums.InternalFunctionProcessing.source_code_is_broken,
                    "#994.280 Meta with key 'output-variable' wasn't found for process '"+executionContextData.process.processCode+"'");
        }

        boolean isArray = MetaUtils.isTrue(executionContextData.process.metas, true, BatchLineSplitterFunction.IS_ARRAY);

        List<List<String>> allLines = stringToListOfList(content, numberOfLines);

        final List<Long> lastIds = new ArrayList<>();
        AtomicInteger currTaskNumber = new AtomicInteger(0);
        String subProcessContextId = ContextUtils.getCurrTaskContextIdForSubProcesses(
                taskParamsYaml.task.taskContextId, executionContextData.subProcesses.get(0).processContextId);

        allLines.forEach( lines -> {
            if (lines.isEmpty()) {
                log.error("#994.290 there isn't any lines");
                return;
            }
            currTaskNumber.incrementAndGet();
            String currTaskContextId = ContextUtils.buildTaskContextId(subProcessContextId, Integer.toString(currTaskNumber.get()));
            String str = StringUtils.join(lines, '\n' );
            VariableData.VariableDataSource variableDataSource = new VariableData.VariableDataSource(str);

            try {
                variableService.createInputVariablesForSubProcess(
                        variableDataSource, simpleExecContext.execContextId, variableName, currTaskContextId, isArray);
            } catch (BatchProcessingException | StoreNewFileWithRedirectException e) {
                throw e;
            } catch (Throwable th) {
                // NAME, TASK_CONTEXT_ID, EXEC_CONTEXT_ID
                String es = S.f("#994.300 An error in createInputVariablesForSubProcess(), name: %s, execContextId: %s, taskCtxId: %s, error: %s",
                        variableName, simpleExecContext.execContextId, currTaskContextId, th.getMessage());
                log.error(es, th);
                throw new BatchResourceProcessingException(es);
            }
            try {
                taskProducingService.createTasksForSubProcesses(graphAndStates, simpleExecContext, executionContextData, currTaskContextId, taskId, lastIds);

            } catch (BatchProcessingException | StoreNewFileWithRedirectException e) {
                throw e;
            } catch (Throwable th) {
                String es = S.f("#994.360 An error in createTasksForSubProcesses(), name: %s, execContextId: %s, taskCtxId: %s, error: %s",
                        variableName, simpleExecContext.execContextId, currTaskContextId, th.getMessage());
                log.error(es, th);
                throw new BatchResourceProcessingException(es);
            }
        });
        execContextGraphService.createEdges(simpleExecContext.execContextGraphId, lastIds, executionContextData.descendants);
    }

    private static class CustomLineIterator extends LineIterator {

        /**
         * Constructs an iterator of the lines for a <code>Reader</code>.
         *
         * @param reader the <code>Reader</code> to read from, not null
         * @throws IllegalArgumentException if the reader is null
         */
        public CustomLineIterator(Reader reader) throws IllegalArgumentException {
            super(reader);
        }

        @Override
        protected boolean isValidLine(String line) {
            //noinspection ConstantConditions
            return line!=null && !line.isBlank();
        }
    }

    private static List<List<String>> stringToListOfList(String content, Long numberOfLines) {
        List<List<String>> allLines = new ArrayList<>();
        LineIterator it = new CustomLineIterator(new InputStreamReader(new ByteArrayInputStream(content.getBytes()), StandardCharsets.UTF_8));

        List<String> currList = new ArrayList<>();
        allLines.add(currList);
        while (it.hasNext()) {
            if (currList.size()==numberOfLines) {
                currList = new ArrayList<>();
                allLines.add(currList);
            }
            currList.add(it.nextLine());
        }
        return allLines;
    }

}
