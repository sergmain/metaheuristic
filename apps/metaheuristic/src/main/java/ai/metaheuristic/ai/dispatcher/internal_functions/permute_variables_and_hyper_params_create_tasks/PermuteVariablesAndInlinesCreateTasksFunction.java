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

package ai.metaheuristic.ai.dispatcher.internal_functions.permute_variables_and_hyper_params_create_tasks;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static ai.metaheuristic.ai.dispatcher.data.InternalFunctionData.InternalFunctionProcessingResult;

/**
 * @author Serge
 * Date: 2/1/2020
 * Time: 9:17 PM
 */
@SuppressWarnings("DuplicatedCode")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class PermuteVariablesAndInlinesCreateTasksFunction implements InternalFunction {

    private final VariableService variableService;
    private final InternalFunctionVariableService internalFunctionVariableService;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final ExecContextGraphService execContextGraphService;
    private final TaskProducingService taskProducingService;
    private final InternalFunctionService internalFunctionService;

    @Override
    public String getCode() {
        return Consts.MH_PERMUTE_VARIABLES_AND_INLINES_CREATE_TASKS_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_PERMUTE_VARIABLES_AND_INLINES_CREATE_TASKS_FUNCTION;
    }

    @SneakyThrows
    @Override
    public void process(
            ExecContextImpl execContext, TaskImpl task, String taskContextId,
            ExecContextParamsYaml.VariableDeclaration variableDeclaration,
            TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxExists();

        if (taskParamsYaml.task.inputs.size()!=1) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.number_of_inputs_is_incorrect,
                    "#991.020 There must be only one an input variable, the actual number: "+taskParamsYaml.task.inputs.size()+", process code: '" + taskParamsYaml.task.processCode+"'"));
        }

        final String varName = taskParamsYaml.task.inputs.get(0).name;
        List<VariableUtils.VariableHolder> hs = internalFunctionVariableService.discoverVariables(execContext.id, taskParamsYaml.task.taskContextId, varName);
        if (hs.size()!=1) {
            throw new InternalFunctionException(
                    new InternalFunctionProcessingResult(
                            Enums.InternalFunctionProcessing.number_of_inputs_is_incorrect,
                            "#991.030 There must be only one an input variable '"+varName+"', the actual number: " + hs.size()));
        }

        VariableUtils.VariableHolder variableHolder = hs.get(0);
        if (variableHolder.variable==null) {
            throw new InternalFunctionException(
                    new InternalFunctionProcessingResult(
                            Enums.InternalFunctionProcessing.source_code_is_broken,
                            "#991.035 An input variable '"+varName+"' must be local variable, not a global variable"));
        }

        String arrayData = variableService.getVariableDataAsString(variableHolder.variable.id);
        VariableArrayParamsYaml vapy = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.to(arrayData);
        if (vapy.array.size()!=1) {
            throw new InternalFunctionException(
                    new InternalFunctionProcessingResult(
                            Enums.InternalFunctionProcessing.number_of_inputs_is_incorrect,
                            "#991.037 There must be only one an input variable in an array variable '"+varName+"', the actual number: " + vapy.array.size()));
        }

        String json = variableService.getVariableDataAsString(Long.valueOf(vapy.array.get(0).id));

        InternalFunctionData.ExecutionContextData executionContextData = internalFunctionService.getSubProcesses(execContext.sourceCodeId, execContext, taskParamsYaml, task.id);
        if (executionContextData.internalFunctionProcessingResult.processing!= Enums.InternalFunctionProcessing.ok) {
            throw new InternalFunctionException(
                executionContextData.internalFunctionProcessingResult);
        }

        if (executionContextData.subProcesses.isEmpty()) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.sub_process_not_found,
                    "#991.040 there isn't any sub-process for process '"+executionContextData.process.processCode+"'"));
        }

        Set<ExecContextData.TaskVertex> descendants = execContextGraphTopLevelService.findDescendants(execContext, task.id);
        if (descendants.isEmpty()) {
            throw new InternalFunctionException(
                    new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.broken_graph_error,
                            "#991.060 Graph for ExecContext #"+ execContext +" is broken"));
        }

        final List<Long> lastIds = new ArrayList<>();
        AtomicInteger currTaskNumber = new AtomicInteger(0);
        String subProcessContextId = executionContextData.subProcesses.get(0).processContextId;

        StringReader sr = new StringReader(json);
        List<String> lines = IOUtils.readLines(sr);

        for (String line : lines) {
            currTaskNumber.incrementAndGet();
            VariableData.Permutation p = VariableUtils.asStringAsPermutation(line);

            VariableData.VariableDataSource variableDataSource = new VariableData.VariableDataSource(p);

            variableService.createInputVariablesForSubProcess(
                    variableDataSource, execContext, currTaskNumber, p.permutedVariableName, subProcessContextId);

            taskProducingService.createTasksForSubProcesses(
                    execContext, executionContextData, currTaskNumber, task.id, lastIds);

        }

        execContextGraphService.createEdges(execContext, lastIds, descendants);
    }
}
