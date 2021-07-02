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
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.ai.utils.TxUtils;
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
import java.util.List;
import java.util.Set;

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
    private final PermuteVariablesAndInlinesCreateTasksTxService permuteVariablesAndInlinesCreateTasksTxService;
    private final InternalFunctionService internalFunctionService;
    private final ExecContextGraphSyncService execContextGraphSyncService;
    private final ExecContextTaskStateSyncService execContextTaskStateSyncService;

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
            ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
            TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxNotExists();

        if (taskParamsYaml.task.inputs.size()!=1) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.number_of_inputs_is_incorrect,
                    "#986.020 There must be only one an input variable, the actual number: "+taskParamsYaml.task.inputs.size()+", process code: '" + taskParamsYaml.task.processCode+"'"));
        }

        final String varName = taskParamsYaml.task.inputs.get(0).name;
        List<VariableUtils.VariableHolder> hs = internalFunctionVariableService.discoverVariables(simpleExecContext.execContextId, taskParamsYaml.task.taskContextId, varName);
        if (hs.size()!=1) {
            throw new InternalFunctionException(
                    new InternalFunctionProcessingResult(
                            Enums.InternalFunctionProcessing.number_of_inputs_is_incorrect,
                            "#986.030 There must be only one an input variable '"+varName+"', the actual number: " + hs.size()));
        }

        VariableUtils.VariableHolder variableHolder = hs.get(0);
        if (variableHolder.variable==null) {
            throw new InternalFunctionException(
                    new InternalFunctionProcessingResult(
                            Enums.InternalFunctionProcessing.source_code_is_broken,
                            "#986.035 An input variable '"+varName+"' must be local variable, not a global variable"));
        }

        String arrayData = variableService.getVariableDataAsString(variableHolder.variable.id);
        VariableArrayParamsYaml vapy = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.to(arrayData);
        if (vapy.array.size()!=1) {
            throw new InternalFunctionException(
                    new InternalFunctionProcessingResult(
                            Enums.InternalFunctionProcessing.number_of_inputs_is_incorrect,
                            "#986.037 There must be only one an input variable in an array variable '"+varName+"', the actual number: " + vapy.array.size()));
        }

        String json = variableService.getVariableDataAsString(Long.valueOf(vapy.array.get(0).id));

        InternalFunctionData.ExecutionContextData executionContextData = internalFunctionService.getSubProcesses(simpleExecContext, taskParamsYaml, taskId);
        if (executionContextData.internalFunctionProcessingResult.processing!= Enums.InternalFunctionProcessing.ok) {
            throw new InternalFunctionException(
                executionContextData.internalFunctionProcessingResult);
        }

        if (executionContextData.subProcesses.isEmpty()) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.sub_process_not_found,
                    "#986.040 there isn't any sub-process for process '"+executionContextData.process.processCode+"'"));
        }

        Set<ExecContextData.TaskVertex> descendants = execContextGraphTopLevelService.findDescendants(simpleExecContext.execContextGraphId, taskId);
        if (descendants.isEmpty()) {
            throw new InternalFunctionException(
                    new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.broken_graph_error,
                            "#986.060 Graph for ExecContext #"+ simpleExecContext.execContextId +" is broken"));
        }

        String subProcessContextId = ContextUtils.getCurrTaskContextIdForSubProcesses(
                taskId, taskParamsYaml.task.taskContextId, executionContextData.subProcesses.get(0).processContextId);

        StringReader sr = new StringReader(json);
        final List<String> lines = IOUtils.readLines(sr);

        execContextGraphSyncService.getWithSync(simpleExecContext.execContextGraphId, ()->
                execContextTaskStateSyncService.getWithSync(simpleExecContext.execContextTaskStateId, ()->
                        permuteVariablesAndInlinesCreateTasksTxService.createTaskForPermutations(
                                simpleExecContext, taskId, executionContextData, descendants, subProcessContextId, lines)));
    }

}
