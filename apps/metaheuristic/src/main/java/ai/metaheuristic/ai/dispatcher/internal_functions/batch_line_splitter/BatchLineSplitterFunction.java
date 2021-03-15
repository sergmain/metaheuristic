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

package ai.metaheuristic.ai.dispatcher.internal_functions.batch_line_splitter;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphService;
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
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.UnzipArchiveException;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
public class BatchLineSplitterFunction implements InternalFunction {

    private final VariableService variableService;
    private final InternalFunctionVariableService internalFunctionVariableService;
    private final InternalFunctionService internalFunctionService;
    private final TaskProducingService taskProducingService;
    private final ExecContextGraphService execContextGraphService;

    @Override
    public String getCode() {
        return Consts.MH_BATCH_LINE_SPLITTER_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_BATCH_LINE_SPLITTER_FUNCTION;
    }

    public void process(
            ExecContextImpl execContext, TaskImpl task, String taskContextId,
            ExecContextParamsYaml.VariableDeclaration variableDeclaration,
            TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxExists();

        // variable-for-splitting
        String inputVariableName = MetaUtils.getValue(taskParamsYaml.task.metas, "variable-for-splitting");
        if (S.b(inputVariableName)) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found, "#994.020 Meta 'variable-for-splitting' wasn't found"));
        }
        // number-of-lines-per-task
        Long numberOfLines = MetaUtils.getLong(taskParamsYaml.task.metas, "number-of-lines-per-task");
        if (numberOfLines==null) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found, "#994.025 Meta 'number-of-lines-per-task' wasn't found"));
        }

        List<VariableUtils.VariableHolder> varHolders = internalFunctionVariableService.discoverVariables(execContext.id, taskContextId, inputVariableName);
        if (varHolders.isEmpty()) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, "#994.030 No input variable was found"));
        }

        if (varHolders.size()>1) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, "#994.040 Too many variables"));
        }

        VariableUtils.VariableHolder variableHolder = varHolders.get(0);

        try {
            String content;
            if (variableHolder.variable!=null) {
                content = variableService.getVariableDataAsString(variableHolder.variable.id);
            }
            else {
                throw new InternalFunctionException(
                    new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, "#994.060 Global variable isn't supported at this time"));
            }
            createTasks(execContext.sourceCodeId, execContext, content, taskParamsYaml, task.id, numberOfLines);
        }
        catch (InternalFunctionException e) {
            throw e;
        }
        catch(UnzipArchiveException e) {
            final String es = "#994.120 can't unzip an archive. Error: " + e.getMessage() + ", class: " + e.getClass();
            log.error(es, e);
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es));
        }
        catch(BatchProcessingException e) {
            final String es = "#994.140 General error of processing batch.\nError: " + e.getMessage();
            log.error(es, e);
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es));
        }
        catch(Throwable th) {
            final String es = "#994.160 General processing error.\nError: " + th.getMessage() + ", class: " + th.getClass();
            log.error(es, th);
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es));
        }
    }

    private void createTasks(
            Long sourceCodeId, ExecContextImpl execContext, String content, TaskParamsYaml taskParamsYaml, Long taskId, Long numberOfLines) throws IOException {

        InternalFunctionData.ExecutionContextData executionContextData = internalFunctionService.getSubProcesses(sourceCodeId, execContext, taskParamsYaml, taskId);
        if (executionContextData.internalFunctionProcessingResult.processing!= Enums.InternalFunctionProcessing.ok) {
            throw new InternalFunctionException(executionContextData.internalFunctionProcessingResult);
        }

        if (executionContextData.subProcesses.isEmpty()) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.sub_process_not_found,
                    "#994.275 there isn't any sub-process for process '"+executionContextData.process.processCode+"'"));
        }

        final String variableName = MetaUtils.getValue(executionContextData.process.metas, "output-variable");
        if (S.b(variableName)) {
            throw new InternalFunctionException(
            new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_is_broken,
                    "#994.280 Meta with key 'output-variable' wasn't found for process '"+executionContextData.process.processCode+"'"));
        }

        List<List<String>> allLines = stringToListOfList(content, numberOfLines);

        final List<Long> lastIds = new ArrayList<>();
        AtomicInteger currTaskNumber = new AtomicInteger(0);
        String subProcessContextId = ContextUtils.getCurrTaskContextIdForSubProcesses(
                taskId, taskParamsYaml.task.taskContextId, executionContextData.subProcesses.get(0).processContextId);

        allLines.forEach( lines -> {
            if (lines.isEmpty()) {
                log.error("#994.290 there isn't any lines");
                return;
            }
            currTaskNumber.incrementAndGet();
            try {
                String str = StringUtils.join(lines, '\n' );

                VariableData.VariableDataSource variableDataSource = new VariableData.VariableDataSource(str);
                String currTaskContextId = ContextUtils.getTaskContextId(subProcessContextId, Integer.toString(currTaskNumber.get()));

                variableService.createInputVariablesForSubProcess(
                        variableDataSource, execContext, variableName, currTaskContextId);

                taskProducingService.createTasksForSubProcesses(
                        execContext, executionContextData, currTaskContextId, taskId, lastIds);

            } catch (BatchProcessingException | StoreNewFileWithRedirectException e) {
                throw e;
            } catch (Throwable th) {
                String es = "#994.300 An error while saving data to file, " + th.toString();
                log.error(es, th);
                throw new BatchResourceProcessingException(es);
            }
        });
        execContextGraphService.createEdges(execContext, lastIds, executionContextData.descendants);
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
            return line!=null && !line.isBlank();
        }
    }

    private static List<List<String>> stringToListOfList(String content, Long numberOfLines) throws IOException {
        List<List<String>> allLines = new ArrayList<>();
        // new LineIterator(new InputStreamReader(input, Charsets.toCharset(encoding)))
//        LineIterator it = IOUtils.lineIterator(new ByteArrayInputStream(content.getBytes()), StandardCharsets.UTF_8);
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
