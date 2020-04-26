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
import ai.metaheuristic.ai.dispatcher.beans.GlobalVariable;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextProcessGraphService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingCoreService;
import ai.metaheuristic.ai.dispatcher.variable.InlineVariable;
import ai.metaheuristic.ai.dispatcher.variable.InlineVariableUtils;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.ai.utils.permutation.Permutation;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
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

    private final VariableRepository variableRepository;
    private final VariableService variableService;
    private final GlobalVariableRepository globalVariableRepository;
    private final TaskProducingCoreService taskProducingCoreService;
    private final ExecContextCache execContextCache;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;

/*
    - key: inline-key
      value: mh.hyper-params
    - key: permute-inline
      value: true
    - key: permutation
      value: var-permutation
*/
    private static final String INLINE_KEY = "inline-key";
    private static final String PERMUTE_INLINE = "permute-inline";

    @Data
    public static class VariableHolder {
        public SimpleVariable variable;
        public GlobalVariable globalVariable;

        public String getName() {
            return globalVariable!=null ? globalVariable.name : variable.variable;
        }
    }

    @Override
    public String getCode() {
        return Consts.MH_PERMUTE_VARIABLES_AND_INLINES_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_PERMUTE_VARIABLES_AND_INLINES_FUNCTION;
    }

    @Override
    public InternalFunctionProcessingResult process(
            Long sourceCodeId, Long execContextId, Long taskId, String taskContextId,
            ExecContextParamsYaml.VariableDeclaration variableDeclaration,
            TaskParamsYaml taskParamsYaml) {

        if (CollectionUtils.isNotEmpty(taskParamsYaml.task.inputs)) {
            log.warn("List of input variables isn't empty");
        }

        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.exec_context_not_found,
                    "ExecContext not found for id #"+execContextId);
        }
        Set<ExecContextData.TaskVertex> descendants = execContextGraphTopLevelService.findDescendants(execContext, taskId);
        if (descendants.isEmpty()) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.broken_graph_error,
                    "Graph for ExecContext #"+execContextId+" is broken");
        }
        ExecContextParamsYaml execContextParamsYaml = execContext.getExecContextParamsYaml();

        final ExecContextParamsYaml.Process process = execContextParamsYaml.findProcess(taskParamsYaml.task.processCode);
        if (process==null) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.process_not_found,
                    "Process '"+taskParamsYaml.task.processCode+"'not found");
        }

        ExecContextParamsYaml wpy = execContext.getExecContextParamsYaml();
        DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph = ExecContextProcessGraphService.importProcessGraph(wpy);

        List<ExecContextData.ProcessVertex> subProcesses = ExecContextProcessGraphService.findSubProcesses(processGraph, process.processCode);

        String variableNames = MetaUtils.getValue(taskParamsYaml.task.metas, "variables");
        if (S.b(variableNames)) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found, "Meta 'variable' must be defined and can't be empty");
        }
        String[] names = StringUtils.split(variableNames, ", ");

        boolean permuteInlines = MetaUtils.isTrue(taskParamsYaml.task.metas, PERMUTE_INLINE);

        Map<String, String> inlines = null;
        final String inlineKey;
        if (MetaUtils.isTrue(taskParamsYaml.task.metas, PERMUTE_INLINE)) {
            inlineKey = MetaUtils.getValue(taskParamsYaml.task.metas, INLINE_KEY);
            if (S.b(inlineKey)) {
                return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found,
                        "Meta 'inline-key' wasn't found or empty.");
            }
            inlines = variableDeclaration.inline.get(inlineKey);
            if (inlines==null || inlines.isEmpty()) {
                return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.inline_not_found,
                        "Inline variable '"+inlineKey+"' wasn't found or empty. List of keys in inlines: " + variableDeclaration.inline.keySet());
            }
        }
        else  {
            inlineKey = null;
        }

        List<VariableHolder> holders = new ArrayList<>();
        for (String name : names) {
            VariableHolder holder = new VariableHolder();
            holders.add(holder);
            SimpleVariable v = variableRepository.findByNameAndTaskContextIdAndExecContextId(name, taskContextId, execContextId);
            if (v!=null) {
                holder.variable = v;
            }
            else {
                GlobalVariable gv = globalVariableRepository.findIdByName(name);
                if (gv!=null) {
                    holder.globalVariable = gv;
                }
                else {
                    return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.variable_not_found,
                            "Variable '"+name+"' not found in local and global contexts, internal context #"+taskContextId);
                }
            }
        }

        final Permutation<VariableHolder> permutation = new Permutation<>();
        AtomicInteger permutationNumber = new AtomicInteger(0);
        final String variableName = MetaUtils.getValue(process.metas, "output-variable");
        if (S.b(variableName)) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.output_variable_not_defined,
                    "Meta with key 'output-variable' wasn't found for process '"+process.processCode+"'");
        }
        final String inlineVariableName = MetaUtils.getValue(process.metas, "inline-permutation");
        if (S.b(inlineVariableName)) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.output_variable_not_defined,
                    "Meta with key 'inline-permutation' wasn't found for process '"+process.processCode+"'");
        }
        final List<Long> lastIds = new ArrayList<>();
        final List<InlineVariable> inlineVariables = InlineVariableUtils.getAllInlineVariants(inlines);
        for (int i = 0; i < holders.size(); i++) {
            try {
                permutation.printCombination(holders, i+1,
                        permutedVariables -> {
                            log.info(permutedVariables.stream().map(VariableHolder::getName).collect(Collectors.joining(", ")));
                            if (permuteInlines) {
                                for (InlineVariable inlineVariable : inlineVariables) {
                                    permutationNumber.incrementAndGet();
                                    Map<String, Map<String, String>> map = new HashMap<>(variableDeclaration.inline);
                                    map.remove(inlineKey);
                                    map.put(inlineKey, inlineVariable.params);

                                    createTasksForSubProcesses(permutedVariables, execContextId, execContextParamsYaml,
                                            subProcesses, permutationNumber, taskId, variableName, map, lastIds,
                                            inlineVariableName, inlineVariable.params
                                    );
                                }
                            }
                            else {
                                permutationNumber.incrementAndGet();
                                createTasksForSubProcesses(permutedVariables, execContextId, execContextParamsYaml, subProcesses, permutationNumber, taskId, variableName,
                                        execContextParamsYaml.variables.inline, lastIds,
                                        inlineVariableName, Map.of()
                                );
                            }
                            return true;
                        }
                );
            } catch (BreakFromLambdaException e) {
                log.error(e.getMessage());
                return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.process_not_found, e.getMessage());
            }
        }
        execContextGraphTopLevelService.createEdges(execContextId, lastIds, descendants);
        return Consts.INTERNAL_FUNCTION_PROCESSING_RESULT_OK;
    }

    public void createTasksForSubProcesses(
            List<VariableHolder> permutedVariables, Long execContextId, ExecContextParamsYaml execContextParamsYaml, List<ExecContextData.ProcessVertex> subProcesses,
            AtomicInteger permutationNumber, Long parentTaskId, String variableName,
            Map<String, Map<String, String>> inlines, List<Long> lastIds, String inlineVariableName, Map<String, String> inlinePermuted) {
        List<Long> parentTaskIds = List.of(parentTaskId);
        TaskImpl t = null;
        String subProcessContextId = null;
        for (ExecContextData.ProcessVertex subProcess : subProcesses) {
            final ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(subProcess.process);
            if (p==null) {
                throw new BreakFromLambdaException("Process '"+subProcess.process+"' wasn't found");
            }
            String currTaskContextId = ContextUtils.getTaskContextId(subProcess.processContextId, Integer.toString(permutationNumber.get()));
            t = taskProducingCoreService.createTaskInternal(execContextId, execContextParamsYaml, p, currTaskContextId, inlines);
            if (t==null) {
                throw new BreakFromLambdaException("Creation of task failed");
            }
            List<Long> currTaskIds = List.of(t.getId());
            execContextGraphTopLevelService.addNewTasksToGraph(execContextId, parentTaskIds, currTaskIds);
            parentTaskIds = currTaskIds;
            // all subProcesses must have the same processContextId
            if (subProcessContextId!=null && !subProcessContextId.equals(subProcess.processContextId)) {
                throw new BreakFromLambdaException("Different contextId, prev: "+ subProcessContextId+", next: " +subProcess.processContextId);
            }
            subProcessContextId = subProcess.processContextId;
        }

        if (subProcessContextId!=null) {
            String currTaskContextId = ContextUtils.getTaskContextId(subProcessContextId, Integer.toString(permutationNumber.get()));
            lastIds.add(t.id);

            {
                VariableArrayParamsYaml vapy = toVariableArrayParamsYaml(permutedVariables);
                String yaml = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.toString(vapy);
                byte[] bytes = yaml.getBytes();
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                Variable v = variableService.createInitialized(bais, bytes.length, variableName, null, execContextId, currTaskContextId);
            }
            {
                Yaml yampUtil = YamlUtils.init(Map.class);
                String yaml = yampUtil.dumpAsMap(inlinePermuted);
                byte[] bytes = yaml.getBytes();
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                Variable v = variableService.createInitialized(bais, bytes.length, inlineVariableName, null, execContextId, currTaskContextId);
            }

        }
    }

    private VariableArrayParamsYaml toVariableArrayParamsYaml(List<VariableHolder> permutedVariables) {
        VariableArrayParamsYaml vapy = new VariableArrayParamsYaml();
        for (VariableHolder pv : permutedVariables) {
            VariableArrayParamsYaml.Variable v = new VariableArrayParamsYaml.Variable();
            if (pv.globalVariable!=null) {
                v.id = pv.globalVariable.id;
                v.name = pv.globalVariable.name;

                DataStorageParams dsp = DataStorageParamsUtils.to(pv.globalVariable.params);
                v.sourcing = dsp.sourcing;
                v.git = dsp.git;
                v.disk = dsp.disk;
                v.realName = pv.globalVariable.filename;
                v.type = EnumsApi.DataType.global_variable;
            }
            else {
                v.id = pv.variable.id;
                v.name = pv.variable.variable;

                DataStorageParams dsp = pv.variable.getParams();
                v.sourcing = dsp.sourcing;
                v.git = dsp.git;
                v.disk = dsp.disk;
                v.realName = pv.variable.originalFilename;
                v.type = EnumsApi.DataType.variable;
            }
            vapy.array.add(v);
        }
        return vapy;
    }

}
