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

package ai.metaheuristic.ai.dispatcher.event;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionProcessor;
import ai.metaheuristic.ai.dispatcher.task.TaskPersistencer;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 3/15/2020
 * Time: 10:58 PM
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("dispatcher")
public class TaskWithInternalContextEventService {

    private final TaskPersistencer taskPersistencer;
    private final TaskSyncService taskSyncService;
    private final InternalFunctionProcessor internalFunctionProcessor;
    private final ExecContextCache execContextCache;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final ApplicationEventPublisher applicationEventPublisher;

    // this code is only for testing
    private static Long lastTaskId=null;
    public static boolean taskFinished(Long id) {
        return id.equals(lastTaskId);
    }

    @Async
    @EventListener
    public void handleAsync(final TaskWithInternalContextEvent event) {
        try {
            taskSyncService.getWithSync(event.taskId, (task) -> {
                try {
                    if (task == null) {
                        log.warn("#707.010 step #1");
                        return null;
                    }
                    if (task.execState == EnumsApi.TaskExecState.IN_PROGRESS.value) {
                        log.error("#707.012 already in progress. mustn't happened. it's, actually, illegal state");
                        return null;
                    }
                    task = taskPersistencer.toInProgressSimpleLambda(event.taskId, task);
                    if (task == null) {
                        log.warn("#707.020 Task #" + event.taskId + " wasn't found");
                        return null;
                    }
                    ExecContextImpl execContext = execContextCache.findById(task.execContextId);
                    if (execContext == null) {
                        taskPersistencer.finishTaskAsBrokenOrError(event.taskId, EnumsApi.TaskExecState.BROKEN, -10000,
                                "#707.030 Task #" + event.taskId + " is broken, execContext #" + task.execContextId + " wasn't found.");
                        return null;
                    }

                    TaskParamsYaml taskParamsYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
                    ExecContextParamsYaml execContextParamsYaml = execContext.getExecContextParamsYaml();
                    ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(taskParamsYaml.task.processCode);
                    if (p == null) {
                        log.warn("#707.040 can't find process '" + taskParamsYaml.task.processCode + "' in execContext with Id #" + execContext.id);
                        return null;
                    }

                    InternalFunctionData.InternalFunctionProcessingResult result = internalFunctionProcessor.process(
                            execContext.sourceCodeId, execContext.id, p.internalContextId, taskParamsYaml);

                    if (result.processing != Enums.InternalFunctionProcessing.ok) {
                        log.error("#707.050 error type: {}, message: {}", result.processing, result.error);
                        taskPersistencer.finishTaskAsBrokenOrError(event.taskId, EnumsApi.TaskExecState.BROKEN, -10001,
                                "#707.060 Task #" + event.taskId + " was finished with status '" + result.processing + "', text of error: " + result.error);
                        return null;
                    }

                    ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult r = new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult();
                    r.taskId = event.taskId;
                    FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec();
                    functionExec.exec = new FunctionApiData.SystemExecResult(taskParamsYaml.task.function.code, true, 0, "");
                    r.result = FunctionExecUtils.toString(functionExec);

                    taskPersistencer.storeExecResult(r, t -> {
                        if (t != null) {
                            execContextGraphTopLevelService.updateTaskExecStateByExecContextId(t.getExecContextId(), t.getId(), t.getExecState());
                        }
                    });
                    Enums.UploadResourceStatus status = taskPersistencer.setResultReceived(event.taskId, true);

                    return null;
                } catch (Throwable th) {
                    taskPersistencer.finishTaskAsBrokenOrError(event.taskId, EnumsApi.TaskExecState.BROKEN, -10002,
                            "#707.070 Task #" + event.taskId + " was processed with error: " + th.getMessage());
                    log.error("Error", th);
                }
                return null;
            });
        } catch (Throwable th) {
            taskPersistencer.finishTaskAsBrokenOrError(event.taskId, EnumsApi.TaskExecState.BROKEN, -10003,
                    "#707.080 Task #" + event.taskId + " was processed with error: " + th.getMessage());
            log.error("Error", th);
        }
        finally {
            lastTaskId = event.taskId;
        }
    }
}
