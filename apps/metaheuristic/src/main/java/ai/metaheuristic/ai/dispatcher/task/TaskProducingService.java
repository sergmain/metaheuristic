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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Monitoring;
import ai.metaheuristic.ai.dispatcher.beans.Ids;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextProcessGraphService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionOutput;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionProcessor;
import ai.metaheuristic.ai.dispatcher.repositories.IdsRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.exceptions.BreakFromForEachException;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskProducingService {

    private final TaskRepository taskRepository;
    private final FunctionService functionService;
    private final VariableService variableService;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final InternalFunctionProcessor internalFunctionProcessor;
    private final IdsRepository idsRepository;
    private final ExecContextProcessGraphService execContextProcessGraphService;

    public TaskData.ProduceTaskResult produceTasks(boolean isPersist, Long sourceCodeId, ExecContextParamsYaml execContextParamsYaml) {
        DirectedAcyclicGraph<String, DefaultEdge> processGraph = execContextProcessGraphService.importProcessGraph(execContextParamsYaml);

        long mill = System.currentTimeMillis();
//        List<SimpleVariableAndStorageUrl> initialInputResourceCodes = variableService.getIdInVariables(
//                execContextParamsYaml.getAllVariables());
        log.debug("#701.180 Resources was acquired for {} ms", System.currentTimeMillis() - mill);

//        TaskData.ResourcePools pools = new TaskData.ResourcePools(initialInputResourceCodes);
//        if (pools.status!= EnumsApi.SourceCodeProducingStatus.OK) {
//            return new TaskData.ProduceTaskResult(pools.status);
//        }

        List<Long> parentTaskIds = new ArrayList<>();
        for (String processCode : processGraph) {
            ExecContextParamsYaml.Process p = execContextParamsYaml.processes.stream().filter(o -> o.processCode.equals(processCode)).findAny().orElse(null);
            if (p == null) {
                return new TaskData.ProduceTaskResult(EnumsApi.TaskProducingStatus.PROCESS_NOT_FOUND_ERROR);
            }
            if (true) {
                throw new NotImplementedException("not yet");
            }
            produceTasksForProcess(isPersist, sourceCodeId, p, parentTaskIds, execContextParamsYaml);
        }


        Monitoring.log("##025", Enums.Monitor.MEMORY);

        return null;
    }


    @SuppressWarnings("Duplicates")
    public TaskData.ProduceTaskResult produceTasksForProcess(
            boolean isPersist, Long sourceCodeId, ExecContextParamsYaml.Process process,
            String internalContextId, ExecContextParamsYaml execContextParamsYaml, Long execContextId,
            List<Long> parentTaskIds) {

        List<TaskParamsYaml.InputVariable> inputs = new ArrayList<>();
        List<TaskParamsYaml.OutputVariable> outputs = new ArrayList<>();

        TaskData.ProduceTaskResult result = new TaskData.ProduceTaskResult();

        for (ExecContextParamsYaml.Variable variable : process.output) {
            Variable v = variableService.createUninitialized(variable.name, execContextId, internalContextId);
            // resourceId is an Id of one part of Variable. Variable can contain unlimited number of resources
            String resourceId = v.id.toString();
            outputs.add( new TaskParamsYaml.OutputVariable(variable.name, variable.sourcing, variable.git, variable.disk, new TaskParamsYaml.Resource(resourceId, EnumsApi.VariableContext.local, null)));

            outputResourceIds.put(resourceId, variable);
            result.outputResourceCodes.add(resourceId);
            inputStorageUrls.put(resourceId, variable);
        }

        if (isPersist) {
            Task t = createTaskInternal(
                    execContextParamsYaml, execContextId, process, outputResourceIds, process.function, pools.collectedInputs, inputStorageUrls, pools.mappingCodeToOriginalFilename);
            if (t!=null) {
                result.taskIds.add(t.getId());
            }
        }

        execContextGraphTopLevelService.addNewTasksToGraph(execContextId, parentTaskIds, result.taskIds);

        result.numberOfTasks++;

        result.status = EnumsApi.TaskProducingStatus.OK;
        return result;
    }

    @SuppressWarnings("Duplicates")
    private TaskImpl createTaskInternal(
            SourceCodeParamsYaml sourceCodeParams, Long execContextId, SourceCodeParamsYaml.Process process,
            Map<String, SourceCodeParamsYaml.Variable> outputResourceIds,
            SourceCodeParamsYaml.FunctionDefForSourceCode snDef, Map<String, List<String>> collectedInputs, Map<String, SourceCodeParamsYaml.Variable> inputStorageUrls,
            Map<String, String> mappingCodeToOriginalFilename) {
        TaskParamsYaml yaml = new TaskParamsYaml();
        yaml.taskYaml.execContextId = execContextId;

        collectedInputs.forEach((key, value) -> yaml.taskYaml.inputResourceIds.put(key, value));
        outputResourceIds.forEach((key, value) -> yaml.taskYaml.outputResourceIds.put(value.name, key));

        yaml.taskYaml.realNames = mappingCodeToOriginalFilename;

        // work around with SnakeYaml's refs
        Map<String, SourceCodeParamsYaml.Variable> map = new HashMap<>();
        for (Map.Entry<String, SourceCodeParamsYaml.Variable> entry : inputStorageUrls.entrySet()) {
            final SourceCodeParamsYaml.Variable v = entry.getValue();
            map.put(entry.getKey(), new SourceCodeParamsYaml.Variable(v.sourcing, v.git, v.disk, v.name));
        }
        yaml.taskYaml.resourceStorageUrls = map;

        yaml.taskYaml.function = functionService.getFunctionConfig(snDef);
        if (yaml.taskYaml.function ==null) {
            log.error("#171.07 Function wasn't found for code: {}", snDef.code);
            return null;
        }
        yaml.taskYaml.preFunctions = new ArrayList<>();
        if (process.getPreFunctions()!=null) {
            for (SourceCodeParamsYaml.FunctionDefForSourceCode preFunction : process.getPreFunctions()) {
                yaml.taskYaml.preFunctions.add(functionService.getFunctionConfig(preFunction));
            }
        }
        yaml.taskYaml.postFunctions = new ArrayList<>();
        if (process.getPostFunctions()!=null) {
            for (SourceCodeParamsYaml.FunctionDefForSourceCode postFunction : process.getPostFunctions()) {
                yaml.taskYaml.postFunctions.add(functionService.getFunctionConfig(postFunction));
            }
        }
        yaml.taskYaml.clean = sourceCodeParams.source.clean;
        yaml.taskYaml.timeoutBeforeTerminate = process.timeoutBeforeTerminate;

        String taskParams = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(yaml);

        TaskImpl task = new TaskImpl();
        task.setExecContextId(execContextId);
        task.setParams(taskParams);
        taskRepository.save(task);

        return task;
    }

}
