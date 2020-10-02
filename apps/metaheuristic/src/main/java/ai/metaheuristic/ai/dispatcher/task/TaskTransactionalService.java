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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.exceptions.TaskCreationException;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.ai.utils.TxUtils;
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
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
    private final TaskRepository taskRepository;
    private final ExecContextCache execContextCache;
    private final ExecContextGraphService execContextGraphService;
    private final ExecContextSyncService execContextSyncService;
    private final FunctionService functionService;

    @Transactional
    public TaskData.ProduceTaskResult produceTaskForProcess(
            boolean isPersist, Long sourceCodeId, ExecContextParamsYaml.Process process,
            ExecContextParamsYaml execContextParamsYaml, Long execContextId,
            List<Long> parentTaskIds) {

        execContextSyncService.checkWriteLockPresent(execContextId);

        TaskData.ProduceTaskResult result = new TaskData.ProduceTaskResult();

        if (isPersist) {
            try {
                // for external Functions internalContextId==process.internalContextId
                TaskImpl t = createTaskInternal(execContextId, execContextParamsYaml, process, process.internalContextId,
                        execContextParamsYaml.variables.inline);
                if (t == null) {
                    result.status = EnumsApi.TaskProducingStatus.TASK_PRODUCING_ERROR;
                    result.error = "#375.080 Unknown reason of error while task creation";
                    return result;
                }
                result.taskId = t.getId();
                List<TaskApiData.TaskWithContext> taskWithContexts = List.of(new TaskApiData.TaskWithContext( t.getId(), process.internalContextId));
                execContextGraphService.addNewTasksToGraph(execContextCache.findById(execContextId), parentTaskIds, taskWithContexts);
            } catch (TaskCreationException e) {
                result.status = EnumsApi.TaskProducingStatus.TASK_PRODUCING_ERROR;
                result.error = "#375.100 task creation error " + e.getMessage();
                log.error(result.error);
                return result;
            }
        }
        result.numberOfTasks=1;
        result.status = EnumsApi.TaskProducingStatus.OK;
        return result;
    }

    public TaskImpl save(TaskImpl task) {
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(task.execContextId);

        try {
/*
            if (log.isDebugEnabled()) {
                log.debug("#462.010 save task, id: #{}, ver: {}, task: {}", task.id, task.version, task);
                try {
                    throw new RuntimeException("task stacktrace");
                }
                catch(RuntimeException e) {
                    log.debug("task stacktrace", e);
                }
            }
*/
            return taskRepository.save(task);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("Current task:\n" + task+"\ntask in db: " + (task.id!=null ? taskRepository.findById(task.id) : null));
            throw e;
        }
    }

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
            t = createTaskInternal(execContextId, execContextParamsYaml, p, currTaskContextId, inlines);
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
            try {
                Variable v = variableService.createInitialized(bais, bytes.length, inputVariableName, null, execContextId, currTaskContextId);
            } catch (Throwable e) {
                ExceptionUtils.rethrow(e);
            }

            int i=0;
        }
    }

    @Nullable
    public TaskImpl createTaskInternal(
            Long execContextId, ExecContextParamsYaml execContextParamsYaml, ExecContextParamsYaml.Process process,
            String taskContextId, @Nullable Map<String, Map<String, String>> inlines) {

        TaskParamsYaml taskParams = new TaskParamsYaml();
        taskParams.task.execContextId = execContextId;
        taskParams.task.taskContextId = taskContextId;
        taskParams.task.processCode = process.processCode;
        taskParams.task.context = process.function.context;
        taskParams.task.metas.addAll(process.metas);
        taskParams.task.inline = inlines;

        // inputs and outputs will be initialized at the time of task selection
        // task selection is here:
        //      ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService.prepareVariables

        if (taskParams.task.context== EnumsApi.FunctionExecContext.internal) {
            taskParams.task.function = new TaskParamsYaml.FunctionConfig(
                    process.function.code, "internal", null, S.b(process.function.params) ? "" : process.function.params,"internal",
                    EnumsApi.FunctionSourcing.dispatcher, null,
                    null, false );
        }
        else {
            TaskParamsYaml.FunctionConfig fConfig = functionService.getFunctionConfig(process.function);
            if (fConfig == null) {
                log.error("#171.020 Function '{}' wasn't found", process.function.code);
                return null;
            }
            taskParams.task.function = fConfig;
            if (process.getPreFunctions()!=null) {
                for (ExecContextParamsYaml.FunctionDefinition preFunction : process.getPreFunctions()) {
                    taskParams.task.preFunctions.add(functionService.getFunctionConfig(preFunction));
                }
            }
            if (process.getPostFunctions()!=null) {
                for (ExecContextParamsYaml.FunctionDefinition postFunction : process.getPostFunctions()) {
                    taskParams.task.postFunctions.add(functionService.getFunctionConfig(postFunction));
                }
            }
        }
        taskParams.task.clean = execContextParamsYaml.clean;
        taskParams.task.timeoutBeforeTerminate = process.timeoutBeforeTerminate;

        String params = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(taskParams);

        TaskImpl task = new TaskImpl();
        task.setExecContextId(execContextId);
        task.setParams(params);
        save(task);

        return task;
    }

    @Nullable
    public TaskImpl persistOutputVariables(TaskImpl task, TaskParamsYaml taskParams, ExecContextImpl execContext, ExecContextParamsYaml.Process p) {
        variableService.initOutputVariables(taskParams, execContext, p);
        return setParams(task.id, taskParams);
    }

    @Nullable
    public TaskImpl setParams(Long taskId, TaskParamsYaml params) {
        return setParams(taskId, TaskParamsYamlUtils.BASE_YAML_UTILS.toString(params));
    }

    @Nullable
    public TaskImpl setParams(Long taskId, String taskParams) {
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#305.082 Can't find Task for Id: {}", taskId);
            return null;
        }
        execContextSyncService.checkWriteLockPresent(task.execContextId);
        return setParamsInternal(taskId, taskParams, task);
//        return taskSyncService.getWithSync(taskId, (task) -> setParamsInternal(taskId, taskParams, task));
    }

    @Nullable
    public TaskImpl setParamsInternal(Long taskId, String taskParams, @Nullable TaskImpl task) {
        TxUtils.checkTxExists();
        try {
            if (task == null) {
                log.warn("#307.010 Task with taskId {} wasn't found", taskId);
                return null;
            }
            task.setParams(taskParams);
            save(task);
            return task;
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#307.020 !!!NEED TO INVESTIGATE. Error set setParams to {}, taskId: {}, error: {}", taskParams, taskId, e.toString());
        }
        return null;
    }

    @Transactional
    public Enums.UploadResourceStatus setResultReceived(TaskImpl task, Long variableId) {
        execContextSyncService.checkWriteLockPresent(task.execContextId);
        if (task.getExecState() == EnumsApi.TaskExecState.NONE.value) {
            log.warn("#307.030 Task {} was reset, can't set new value to field resultReceived", task.id);
            return Enums.UploadResourceStatus.TASK_WAS_RESET;
        }
        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
        TaskParamsYaml.OutputVariable output = tpy.task.outputs.stream().filter(o->o.id.equals(variableId)).findAny().orElse(null);
        if (output==null) {
            return Enums.UploadResourceStatus.UNRECOVERABLE_ERROR;
        }
        output.uploaded = true;
        task.params = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(tpy);

        boolean allUploaded = tpy.task.outputs.isEmpty() || tpy.task.outputs.stream().allMatch(o -> o.uploaded);
        task.setCompleted(allUploaded);
        task.setCompletedOn(System.currentTimeMillis());
        task.setResultReceived(allUploaded);
        save(task);
        return Enums.UploadResourceStatus.OK;
    }

    public Enums.UploadResourceStatus setResultReceivedForInternalFunction(Long taskId) {
        TxUtils.checkTxExists();

        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#317.020 Task #{} is obsolete and was already deleted", taskId);
            return Enums.UploadResourceStatus.TASK_NOT_FOUND;
        }
        execContextSyncService.checkWriteLockPresent(task.execContextId);

        if (task.getExecState() == EnumsApi.TaskExecState.NONE.value) {
            log.warn("#307.080 Task {} was reset, can't set new value to field resultReceived", taskId);
            return Enums.UploadResourceStatus.TASK_WAS_RESET;
        }
        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
        tpy.task.outputs.forEach(o->o.uploaded = true);
        task.params = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(tpy);
        task.setCompleted(true);
        task.setCompletedOn(System.currentTimeMillis());
        task.setResultReceived(true);
        save(task);
        return Enums.UploadResourceStatus.OK;
    }

    @Transactional
    public Void deleteOrphanTasks(Long execContextId) {
        List<Long> ids = taskRepository.findAllByExecContextId(Consts.PAGE_REQUEST_100_REC, execContextId) ;
        if (ids.isEmpty()) {
            return null;
        }
        for (Long id : ids) {
            log.info("Found orphan task #{}, execContextId: #{}", id, execContextId);
            taskRepository.deleteById(id);
        }
        return null;
    }


}
