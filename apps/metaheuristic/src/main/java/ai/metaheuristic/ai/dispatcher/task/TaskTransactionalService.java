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

import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Serge
 * Date: 9/23/2020
 * Time: 11:39 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskTransactionalService {

    private final VariableService variableService;
    private final TaskProducingCoreService taskProducingCoreService;
    private final TaskRepository taskRepository;
    private final TaskPersistencer taskPersistencer;
    private final ExecContextCache execContextCache;
    private final ExecContextGraphService execContextGraphService;

    @Transactional(readOnly = true)
    public List<TaskData.SimpleTaskInfo> getSimpleTaskInfos(Long execContextId) {
        try (Stream<TaskImpl> stream = taskRepository.findAllByExecContextIdAsStream(execContextId)) {
            return stream.map(o-> {
                TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(o.params);
                return new TaskData.SimpleTaskInfo(o.id,  EnumsApi.TaskExecState.from(o.execState).toString(), tpy.task.taskContextId, tpy.task.processCode, tpy.task.function.code);
            }).collect(Collectors.toList());
        }
    }

    /**
     * @param files
     * @param execContextId
     * @param currTaskNumber
     * @param parentTaskId
     * @param inputVariableName
     * @param lastIds
     */
    public void createTasksForSubProcesses(
            Stream<BatchTopLevelService.FileWithMapping> files, @Nullable String inputVariableContent, Long execContextId, InternalFunctionData.ExecutionContextData executionContextData,
            AtomicInteger currTaskNumber, Long parentTaskId, String inputVariableName,
            List<Long> lastIds) {

        ExecContextParamsYaml execContextParamsYaml = executionContextData.execContextParamsYaml;
        List<ExecContextData.ProcessVertex> subProcesses = executionContextData.subProcesses;
        Map<String, Map<String, String>> inlines = executionContextData.execContextParamsYaml.variables.inline;
        ExecContextParamsYaml.Process process = executionContextData.process;

        List<Long> parentTaskIds = List.of(parentTaskId);
        TaskImpl t = null;
        String subProcessContextId = null;
        for (ExecContextData.ProcessVertex subProcess : subProcesses) {
            final ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(subProcess.process);
            if (p==null) {
                throw new BreakFromLambdaException("#995.320 Process '" + subProcess.process + "' wasn't found");
            }

            if (process.logic!= EnumsApi.SourceCodeSubProcessLogic.sequential) {
                throw new BreakFromLambdaException("#995.340 only the 'sequential' logic is supported");
            }
            // all subProcesses must have the same processContextId
            if (subProcessContextId!=null && !subProcessContextId.equals(subProcess.processContextId)) {
                throw new BreakFromLambdaException("#995.360 Different contextId, prev: "+ subProcessContextId+", next: " +subProcess.processContextId);
            }

            String currTaskContextId = ContextUtils.getTaskContextId(subProcess.processContextId, Integer.toString(currTaskNumber.get()));
            t = taskProducingCoreService.createTaskInternal(execContextId, execContextParamsYaml, p, currTaskContextId, inlines);
            if (t==null) {
                throw new BreakFromLambdaException("#995.380 Creation of task failed");
            }
            List<TaskApiData.TaskWithContext> currTaskIds = List.of(new TaskApiData.TaskWithContext(t.getId(), currTaskContextId));
            execContextGraphService.addNewTasksToGraph(execContextCache.findById(execContextId), parentTaskIds, currTaskIds);
            parentTaskIds = List.of(t.getId());
            subProcessContextId = subProcess.processContextId;
        }

        if (subProcessContextId!=null) {
            String currTaskContextId = ContextUtils.getTaskContextId(subProcessContextId, Integer.toString(currTaskNumber.get()));
            lastIds.add(t.id);

            List<VariableUtils.VariableHolder> variableHolders = files
                    .map(f-> {
                        String variableName = S.f("mh.array-element-%s-%d", UUID.randomUUID().toString(), System.currentTimeMillis());

                        Variable v;
                        try {
                            try( FileInputStream fis = new FileInputStream(f.file)) {
                                v = variableService.createInitialized(fis, f.file.length(), variableName, f.originName, execContextId, currTaskContextId);
                            }
                        } catch (IOException e) {
                            throw new BreakFromLambdaException(e);
                        }
                        SimpleVariable sv = new SimpleVariable(v.id, v.name, v.params, v.filename, v.inited, v.nullified, v.taskContextId);
                        return new VariableUtils.VariableHolder(sv);
                    })
                    .collect(Collectors.toList());


            if (!S.b(inputVariableContent)) {
                String variableName = S.f("mh.array-element-%s-%d", UUID.randomUUID().toString(), System.currentTimeMillis());
                Variable v;
                try {
                    final byte[] bytes = inputVariableContent.getBytes();
                    try(InputStream is = new ByteArrayInputStream(bytes)) {
                        v = variableService.createInitialized(is, bytes.length, variableName, variableName, execContextId, currTaskContextId);
                    }
                } catch (IOException e) {
                    throw new BreakFromLambdaException(e);
                }
                SimpleVariable sv = new SimpleVariable(v.id, v.name, v.params, v.filename, v.inited, v.nullified, v.taskContextId);
                VariableUtils.VariableHolder variableHolder = new VariableUtils.VariableHolder(sv);
                variableHolders.add(variableHolder);
            }

            VariableArrayParamsYaml vapy = VariableUtils.toVariableArrayParamsYaml(variableHolders);
            String yaml = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.toString(vapy);
            byte[] bytes = yaml.getBytes();
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            Variable v = variableService.createInitialized(bais, bytes.length, inputVariableName, null, execContextId, currTaskContextId);

            int i=0;
        }
    }

    public void persistOutputVariables(TaskImpl task, TaskParamsYaml taskParams, ExecContextImpl execContext, ExecContextParamsYaml.Process p) {
        variableService.initOutputVariables(taskParams, execContext, p);
        taskPersistencer.setParams(task.id, taskParams);
    }

}
