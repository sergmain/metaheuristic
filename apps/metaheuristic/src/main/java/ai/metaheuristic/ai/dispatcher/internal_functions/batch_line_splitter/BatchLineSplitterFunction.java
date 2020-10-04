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

package ai.metaheuristic.ai.dispatcher.internal_functions.batch_line_splitter;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.exceptions.BatchProcessingException;
import ai.metaheuristic.ai.exceptions.BatchResourceProcessingException;
import ai.metaheuristic.ai.exceptions.StoreNewFileWithRedirectException;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.UnzipArchiveException;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static ai.metaheuristic.ai.Consts.INTERNAL_FUNCTION_PROCESSING_RESULT_OK;
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
    private final ExecContextCache execContextCache;

    @Override
    public String getCode() {
        return Consts.MH_BATCH_LINE_SPLITTER_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_BATCH_LINE_SPLITTER_FUNCTION;
    }

    @Transactional
    public InternalFunctionProcessingResult process(
            @NonNull Long sourceCodeId, @NonNull Long execContextId, @NonNull Long taskId, @NonNull String taskContextId,
            @NonNull ExecContextParamsYaml.VariableDeclaration variableDeclaration,
            @NonNull TaskParamsYaml taskParamsYaml, VariableData.DataStreamHolder holder) {

        // variable-for-splitting
        String inputVariableName = MetaUtils.getValue(taskParamsYaml.task.metas, "variable-for-splitting");
        if (S.b(inputVariableName)) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found, "#994.020 Meta 'variable-for-splitting' wasn't found");
        }
        // number-of-lines-per-task
        Long numberOfLines = MetaUtils.getLong(taskParamsYaml.task.metas, "number-of-lines-per-task");
        if (numberOfLines==null) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found, "#994.025 Meta 'number-of-lines-per-task' wasn't found");
        }

        List<VariableUtils.VariableHolder> varHolders = new ArrayList<>();
        InternalFunctionProcessingResult result = internalFunctionVariableService.discoverVariables(execContextId, taskContextId, inputVariableName, varHolders);
        if (result != null) {
            return result;
        }
        if (varHolders.isEmpty()) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, "#994.030 No input variable was found");
        }

        if (varHolders.size()>1) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, "#994.040 Too many variables");
        }

        VariableUtils.VariableHolder variableHolder = varHolders.get(0);

        try {
            String content;
            if (variableHolder.variable!=null) {
                content = variableService.getVariableDataAsString(variableHolder.variable.id);
            }
            else {
                return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, "#994.060 Global variable isn't supported at this time");
            }
            return createTasks(sourceCodeId, execContextId, content, taskParamsYaml, taskId, numberOfLines, holder);
        }
        catch(UnzipArchiveException e) {
            final String es = "#994.120 can't unzip an archive. Error: " + e.getMessage() + ", class: " + e.getClass();
            log.error(es, e);
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es);
        }
        catch(BatchProcessingException e) {
            final String es = "#994.140 General error of processing batch.\nError: " + e.getMessage();
            log.error(es, e);
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es);
        }
        catch(Throwable th) {
            final String es = "#994.160 General processing error.\nError: " + th.getMessage() + ", class: " + th.getClass();
            log.error(es, th);
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es);
        }
    }

    private InternalFunctionProcessingResult createTasks(
            Long sourceCodeId, Long execContextId, String content, TaskParamsYaml taskParamsYaml, Long taskId, Long numberOfLines, VariableData.DataStreamHolder holder) throws IOException {

        InternalFunctionData.ExecutionContextData executionContextData = internalFunctionService.getSupProcesses(sourceCodeId, execContextId, taskParamsYaml, taskId);
        if (executionContextData.internalFunctionProcessingResult.processing!= Enums.InternalFunctionProcessing.ok) {
            return executionContextData.internalFunctionProcessingResult;
        }

        final String variableName = MetaUtils.getValue(executionContextData.process.metas, "output-variable");
        if (S.b(variableName)) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_is_broken,
                    "#994.280 Meta with key 'output-variable' wasn't found for process '"+executionContextData.process.processCode+"'");
        }

        List<List<String>> allLines = stringToListOfList(content, numberOfLines);

        final List<Long> lastIds = new ArrayList<>();
        AtomicInteger currTaskNumber = new AtomicInteger(0);
        allLines.forEach( lines ->  {
            currTaskNumber.incrementAndGet();
            try {
                String str = StringUtils.join(lines, '\n' );
                taskProducingService.createTasksForSubProcesses(
                        Stream.empty(), str, execContextId, executionContextData, currTaskNumber, taskId, variableName, lastIds, holder );
            } catch (BatchProcessingException | StoreNewFileWithRedirectException e) {
                throw e;
            } catch (Throwable th) {
                String es = "#994.300 An error while saving data to file, " + th.toString();
                log.error(es, th);
                throw new BatchResourceProcessingException(es);
            }
        });
        execContextGraphService.createEdges(execContextCache.findById(execContextId), lastIds, executionContextData.descendants);
        return INTERNAL_FUNCTION_PROCESSING_RESULT_OK;
    }

    private static List<List<String>> stringToListOfList(String content, Long numberOfLines) throws IOException {
        List<List<String>> allLines = new ArrayList<>();
        LineIterator it = IOUtils.lineIterator(new ByteArrayInputStream(content.getBytes()), StandardCharsets.UTF_8);
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
