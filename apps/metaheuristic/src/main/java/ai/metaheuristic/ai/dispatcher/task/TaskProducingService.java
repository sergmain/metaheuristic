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
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariableAndStorageUrl;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.exceptions.BreakFromForEachException;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public TaskData.ProduceTaskResult produceTasks(ExecContextParamsYaml execContextParamsYaml) {
        DirectedAcyclicGraph<String, DefaultEdge> processGraph = execContextProcessGraphService.importProcessGraph(execContextParamsYaml);

        long mill = System.currentTimeMillis();
//        List<SimpleVariableAndStorageUrl> initialInputResourceCodes = variableService.getIdInVariables(
//                execContextParamsYaml.getAllVariables());
        log.debug("#701.180 Resources was acquired for {} ms", System.currentTimeMillis() - mill);

//        TaskData.ResourcePools pools = new TaskData.ResourcePools(initialInputResourceCodes);
//        if (pools.status!= EnumsApi.SourceCodeProducingStatus.OK) {
//            return new TaskData.ProduceTaskResult(pools.status);
//        }

        // todo 2020-02-15 what do we do here?
        // todo 2020-02-25 need to recall what this 'preservePoolNames' about
        if (execContextParamsYaml.execContextYaml.preservePoolNames) {
            final Map<String, List<String>> collectedInputs = new HashMap<>();
            try {
                pools.collectedInputs.forEach( (key, value) -> {
                    String newKey = execContextParamsYaml.execContextYaml.variables.entrySet().stream()
                            .filter(entry -> entry.getValue().contains(key))
                            .findFirst()
                            .map(Map.Entry::getKey)
                            .orElseThrow(()-> {
                                log.error("#701.190 Can't find key for pool code {}", key );
                                throw new BreakFromForEachException();
                            });
                    collectedInputs.put(newKey, value);
                });
            } catch (BreakFromForEachException e) {
                return new TaskData.ProduceTaskResult(EnumsApi.SourceCodeProducingStatus.ERROR);
            }

            pools.collectedInputs.clear();
            pools.collectedInputs.putAll(collectedInputs);
        }

        Monitoring.log("##025", Enums.Monitor.MEMORY);

        return null;
    }


    @SuppressWarnings("Duplicates")
    public TaskData.ProduceTaskResult produceTasksForProcess(
            boolean isPersist, Long sourceCodeId, String internalContextId, SourceCodeParamsYaml sourceCodeParams, Long execContextId,
            SourceCodeParamsYaml.Process process, TaskData.ResourcePools pools, List<Long> parentTaskIds) {

        Map<String, SourceCodeParamsYaml.Variable> inputStorageUrls = new HashMap<>(pools.inputStorageUrls);

        TaskData.ProduceTaskResult result = new TaskData.ProduceTaskResult();

        result.outputResourceCodes = new ArrayList<>();

        SourceCodeParamsYaml.FunctionDefForSourceCode snDef = process.function;
        // start processing of process
        if (process.function.context== EnumsApi.FunctionExecContext.external ) {
            Map<String, SourceCodeParamsYaml.Variable> outputResourceIds = new HashMap<>();
            for (SourceCodeParamsYaml.Variable variable : process.output) {
                Variable v = variableService.createUninitialized(variable.name, execContextId, internalContextId);
                // resourceId is an Id of one part of Variable. Variable can contains unlimited number of resources
                String resourceId = v.id.toString();
                outputResourceIds.put(resourceId, variable);
                result.outputResourceCodes.add(resourceId);
                inputStorageUrls.put(resourceId, variable);
            }
            if (isPersist) {
                Task t = createTaskInternal(sourceCodeParams, execContextId, process, outputResourceIds, snDef, pools.collectedInputs, inputStorageUrls, pools.mappingCodeToOriginalFilename);
                if (t!=null) {
                    result.taskIds.add(t.getId());
                }
            }

            execContextGraphTopLevelService.addNewTasksToGraph(execContextId, parentTaskIds, result.taskIds);

            result.numberOfTasks++;
            if (process.subProcesses!=null && CollectionUtils.isNotEmpty(process.subProcesses.processes)) {
                for (SourceCodeParamsYaml.Process subProcess : process.subProcesses.processes) {
                    // Right we don't support subProcesses in subProcesses. Need to collect more info about such cases
                    if (subProcess.subProcesses!=null && CollectionUtils.isNotEmpty(subProcess.subProcesses.processes)) {
                        return new TaskData.ProduceTaskResult(EnumsApi.SourceCodeProducingStatus.TOO_MANY_LEVELS_OF_SUBPROCESSES_ERROR);
                    }

                    String ctxId = internalContextId + ','+ idsRepository.save(new Ids()).id;

                    for (SourceCodeParamsYaml.Variable variable : subProcess.output) {
                        Variable v = variableService.createUninitialized(variable.name, execContextId, ctxId);
                        // resourceId is an Id of one part of Variable. Variable can contains unlimited number of resources
                        String resourceId = v.id.toString();
                        outputResourceIds.put(resourceId, variable);
                        result.outputResourceCodes.add(resourceId);
                        inputStorageUrls.put(resourceId, variable);
                    }
                    if (isPersist) {
                        Task t = createTaskInternal(sourceCodeParams, execContextId, process, outputResourceIds, snDef, pools.collectedInputs, inputStorageUrls, pools.mappingCodeToOriginalFilename);
                        if (t!=null) {
                            result.taskIds.add(t.getId());
                        }
                    }
                    result.numberOfTasks++;
                }
            }
        }
        else {
            if (true) {
                throw new NotImplementedException("not yet");
            }
            // variables will be created while processing of internal function
            List<InternalFunctionOutput> outputs = internalFunctionProcessor.process(snDef.code, sourceCodeId, execContextId, internalContextId, null);

            // theoretically, internal function can be without subProcesses, i.e. a result aggregation function

            if (process.subProcesses!=null && CollectionUtils.isNotEmpty(process.subProcesses.processes)) {
                for (SourceCodeParamsYaml.Process subProcess : process.subProcesses.processes) {


                    result.numberOfTasks++;
                }
            }

        }
        // end processing the main process

        result.status = EnumsApi.SourceCodeProducingStatus.OK;
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
