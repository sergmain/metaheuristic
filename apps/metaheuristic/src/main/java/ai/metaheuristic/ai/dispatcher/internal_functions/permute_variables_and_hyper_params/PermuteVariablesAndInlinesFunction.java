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

package ai.metaheuristic.ai.dispatcher.internal_functions.permute_variables_and_hyper_params;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.commons.DataHolder;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InlineVariableData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.dispatcher.variable.InlineVariable;
import ai.metaheuristic.ai.dispatcher.variable.InlineVariableUtils;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.utils.permutation.Permutation;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.dispatcher.data.InternalFunctionData.InternalFunctionProcessingResult;

/**
 * @author Serge
 * Date: 2/1/2020
 * Time: 9:17 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class PermuteVariablesAndInlinesFunction implements InternalFunction {

    private final VariableService variableService;
    private final InternalFunctionVariableService internalFunctionVariableService;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final ExecContextGraphService execContextGraphService;
    private final TaskProducingService taskProducingService;
    private final InternalFunctionService internalFunctionService;

    @Override
    public String getCode() {
        return Consts.MH_PERMUTE_VARIABLES_AND_INLINES_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_PERMUTE_VARIABLES_AND_INLINES_FUNCTION;
    }

    @Override
    @Transactional
    public InternalFunctionProcessingResult process(
            @NonNull ExecContextImpl execContext, @NonNull TaskImpl task, @NonNull String taskContextId,
            @NonNull ExecContextParamsYaml.VariableDeclaration variableDeclaration,
            @NonNull TaskParamsYaml taskParamsYaml, DataHolder holder) {

        if (CollectionUtils.isNotEmpty(taskParamsYaml.task.inputs)) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.number_of_inputs_is_incorrect,
                    "#991.020 The function 'mh.permute-variables-and-inlines' can't have input variables, process code: '" + taskParamsYaml.task.processCode+"'");
        }

        InternalFunctionData.ExecutionContextData executionContextData = internalFunctionService.getSubProcesses(execContext.sourceCodeId, execContext, taskParamsYaml, task.id);
        if (executionContextData.internalFunctionProcessingResult.processing!= Enums.InternalFunctionProcessing.ok) {
            return executionContextData.internalFunctionProcessingResult;
        }

        if (executionContextData.subProcesses.isEmpty()) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.sub_process_not_found,
                    "#991.040 there isn't any sub-process for process '"+executionContextData.process.processCode+"'");
        }


        Set<ExecContextData.TaskVertex> descendants = execContextGraphTopLevelService.findDescendants(execContext, task.id);
        if (descendants.isEmpty()) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.broken_graph_error,
                    "#991.060 Graph for ExecContext #"+ execContext +" is broken");
        }
        ExecContextParamsYaml execContextParamsYaml = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(execContext.params);

        final ExecContextParamsYaml.Process process = execContextParamsYaml.findProcess(taskParamsYaml.task.processCode);
        if (process==null) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.process_not_found,
                    "#991.080 Process '"+taskParamsYaml.task.processCode+"'not found");
        }

        // variableNames contains a list of variables for permutation
        String variableNames = MetaUtils.getValue(taskParamsYaml.task.metas, "variables-for-permutation");
        if (S.b(variableNames)) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found,
                    "#991.100 Meta 'variables-for-permutation' must be defined and can't be empty");
        }
        String[] names = StringUtils.split(variableNames, ", ");

        boolean permuteInlines = MetaUtils.isTrue(taskParamsYaml.task.metas, InlineVariableUtils.PERMUTE_INLINE);

        InlineVariableData.InlineVariableItem item = InlineVariableUtils.getInlineVariableItem(variableDeclaration, taskParamsYaml.task.metas);
        if (S.b(item.inlineKey)) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found,
                    "#991.120 Meta 'inline-key' wasn't found or empty.");
        }
        if (item.inlines == null || item.inlines.isEmpty()) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.inline_not_found,
                    "#991.140 Inline variable '" + item.inlineKey + "' wasn't found or empty. List of keys in inlines: " + variableDeclaration.inline.keySet());
        }

        List<VariableUtils.VariableHolder> holders = new ArrayList<>();
        InternalFunctionProcessingResult result = internalFunctionVariableService.discoverVariables(execContext.id, taskContextId, names, holders);
        if (result != null) {
            return result;
        }

        final Permutation<VariableUtils.VariableHolder> permutation = new Permutation<>();
        final String variableName = MetaUtils.getValue(process.metas, "output-variable");
        if (S.b(variableName)) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found,
                    "#991.160 Meta with key 'output-variable' wasn't found for process '"+process.processCode+"'");
        }
        final String inlineVariableName = MetaUtils.getValue(process.metas, "inline-permutation");
        if (S.b(inlineVariableName)) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found,
                    "#991.180 Meta with key 'inline-permutation' wasn't found for process '"+process.processCode+"'");
        }
        final List<Long> lastIds = new ArrayList<>();
        final List<InlineVariable> inlineVariables = InlineVariableUtils.getAllInlineVariants(item.inlines);
        AtomicInteger currTaskNumber = new AtomicInteger(0);
        String subProcessContextId = executionContextData.subProcesses.get(0).processContextId;

        for (int i = 0; i < holders.size(); i++) {
            try {
                permutation.printCombination(holders, i+1,
                        permutedVariables -> {
                            log.info(permutedVariables.stream().map(VariableUtils.VariableHolder::getName).collect(Collectors.joining(", ")));
                            if (permuteInlines) {
                                for (InlineVariable inlineVariable : inlineVariables) {
                                    currTaskNumber.incrementAndGet();
                                    Map<String, Map<String, String>> map = new HashMap<>(variableDeclaration.inline);
                                    map.put(item.inlineKey, inlineVariable.params);

                                    VariableData.VariableDataSource variableDataSource = new VariableData.VariableDataSource(
                                            new VariableData.Permutation(permutedVariables, variableName, map, inlineVariableName, inlineVariable.params));

                                    variableService.createInputVariablesForSubProcess(
                                            variableDataSource, execContext, currTaskNumber, variableName, subProcessContextId, holder);

                                    taskProducingService.createTasksForSubProcesses(
                                            execContext, executionContextData, currTaskNumber, task.id, lastIds, holder);
                                }
                            }
                            else {
                                currTaskNumber.incrementAndGet();

                                VariableData.VariableDataSource variableDataSource = new VariableData.VariableDataSource(
                                        new VariableData.Permutation(permutedVariables, variableName, execContextParamsYaml.variables.inline, inlineVariableName,Map.of()));

                                variableService.createInputVariablesForSubProcess(
                                        variableDataSource, execContext, currTaskNumber, variableName, subProcessContextId, holder);

                                taskProducingService.createTasksForSubProcesses(
                                        execContext, executionContextData, currTaskNumber, task.id, lastIds, holder);
                            }
                            return true;
                        }
                );
            } catch (BreakFromLambdaException e) {
                log.error(e.getMessage());
                return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_is_broken, e.getMessage());
            }
        }
        execContextGraphService.createEdges(execContext, lastIds, descendants);
        return Consts.INTERNAL_FUNCTION_PROCESSING_RESULT_OK;
    }
}
