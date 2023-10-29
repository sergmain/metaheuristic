/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.beans.CacheProcess;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.events.SetVariableReceivedTxEvent;
import ai.metaheuristic.ai.dispatcher.event.events.UpdateTaskExecStatesInGraphTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.repositories.CacheProcessRepository;
import ai.metaheuristic.ai.dispatcher.repositories.CacheVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.storage.DispatcherBlobStorage;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.exceptions.InvalidateCacheProcessException;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * @author Serge
 * Date: 10/30/2020
 * Time: 7:25 PM
 */
@SuppressWarnings("DuplicatedCode")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class TaskCheckCachingTxService {

    private final ExecContextCache execContextCache;
    private final TaskRepository taskRepository;
    private final TaskStateTxService taskStateService;
    private final CacheProcessRepository cacheProcessRepository;
    private final CacheVariableRepository cacheVariableRepository;
    private final VariableTxService variableService;
    private final EventPublisherService eventPublisherService;
    private final DispatcherBlobStorage dispatcherBlobStorage;

    @Transactional
    public void invalidateCacheItemAndSetTaskToNone(Long execContextId, Long taskId, Long cacheProcessId) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            log.info("609.020 ExecContext #{} doesn't exists", execContextId);
            return;
        }
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            return;
        }

        invalidateCacheItemInternal(cacheProcessId);
        taskStateService.updateTaskExecStates(task, EnumsApi.TaskExecState.NONE);
    }

    @Transactional
    public void invalidateCacheItem(Long cacheProcessId) {
        invalidateCacheItemInternal(cacheProcessId);
    }

    public void invalidateCacheItemInternal(Long cacheProcessId) {
        cacheVariableRepository.deleteByCacheProcessId(cacheProcessId);
        cacheProcessRepository.deleteById(cacheProcessId);
    }

    public enum CheckCachingStatus {task_not_found, isnt_check_cache_state, copied_from_cache, no_prev_cache}

    @Transactional
    public CheckCachingStatus checkCaching(Long execContextId, Long taskId, @Nullable CacheProcess cacheProcess) {

        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.debug("609.009 task #{} wasn't found", taskId);
            return CheckCachingStatus.task_not_found;
        }
        if (task.execState!=EnumsApi.TaskExecState.CHECK_CACHE.value) {
            log.info("609.010 task #{} was already checked for cached variables", taskId);
            return CheckCachingStatus.isnt_check_cache_state;
        }

        TaskParamsYaml tpy = task.getTaskParamsYaml();

        CheckCachingStatus status;
        if (cacheProcess!=null) {
            log.info("609.060 cached data was found for task #{}, variables will be copied and will task be set as OK", taskId);
            // finish task with cached data

            List<Object[]> vars = cacheVariableRepository.getVarsByCacheProcessId(cacheProcess.id);
            if (vars.size()!=tpy.task.outputs.size()) {
                log.warn("609.080 cashProcess #{} is broken. Number of stored variable is {} but expected {}. CacheProcess will be invalidated", cacheProcess.id, vars.size(), tpy.task.outputs.size());
                throw new InvalidateCacheProcessException(execContextId, taskId, cacheProcess.id);
            }
            for (TaskParamsYaml.OutputVariable output : tpy.task.outputs) {
                boolean found = false;
                for (Object[] var : vars) {
                    if (var[1].equals(output.name)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    log.warn("""
                        609.100 cacheProcess #{} is broken. output variable {} wasn't found. CacheProcess will be invalidated.
                        vars[0]: {}
                        vars[1]: {}
                        vars[2]: {}
                        """,
                            cacheProcess.id, output.name,
                            vars.get(0)[0]!=null?vars.get(0)[0].getClass().getName():null,
                            vars.get(0)[1]!=null?vars.get(0)[1].getClass().getName():null,
                            vars.get(0)[2]!=null?vars.get(0)[2].getClass().getName():null
                    );
                    log.warn("tpy.task.outputs:");
                    tpy.task.outputs.forEach(o->log.warn("'\t{}", o.toString()));
                    log.warn("vars:");
                    vars.forEach(o->log.warn("'\t{}", Arrays.toString(o)));
                    throw new InvalidateCacheProcessException(execContextId, taskId, cacheProcess.id);
                }
            }

            for (TaskParamsYaml.OutputVariable output : tpy.task.outputs) {
                Object[] obj = vars.stream().filter(o->o[1].equals(output.name)).findFirst().orElseThrow(()->new IllegalStateException("609.120 ???? How???"));
                try {
                    VariableData.StoredVariable storedVariable = new VariableData.StoredVariable( ((Number)obj[0]).longValue(), (String)obj[1], Boolean.TRUE.equals(obj[2]));
                    if (storedVariable.nullified) {
                        VariableSyncService.getWithSyncVoidForCreation(output.id, () -> variableService.setVariableAsNull(taskId, output.id));
                    }
                    else {
                        dispatcherBlobStorage.copyVariableData(storedVariable, output);
                    }
                    eventPublisherService.publishSetVariableReceivedTxEvent(new SetVariableReceivedTxEvent(taskId, output.id, storedVariable.nullified));

                    output.uploaded = true;

                } catch (BreakFromLambdaException e) {
                    log.warn("609.160 error", e);
                    throw new InvalidateCacheProcessException(execContextId, taskId, cacheProcess.id);
                }
            }
            tpy.task.fromCache = true;
            task.updateParams(tpy);

            FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec();
            functionExec.exec = new FunctionApiData.SystemExecResult(tpy.task.function.code, true, 0,
                    "Process was finished with cached data, cacheProcessId: "+ cacheProcess.id);

            task.setFunctionExecResults(FunctionExecUtils.toString(functionExec));
            task.setResultReceived(1);

            task.execState = EnumsApi.TaskExecState.OK.value;

            task.setCompleted(1);
            task.setCompletedOn(System.currentTimeMillis());

            taskRepository.save(task);
            status = CheckCachingStatus.copied_from_cache;
        }
        else {
            log.info("609.080 cached data wasn't found for task #{}", taskId);
            taskStateService.updateTaskExecStates(task, EnumsApi.TaskExecState.NONE);
            status = CheckCachingStatus.no_prev_cache;
        }
        eventPublisherService.publishUpdateTaskExecStatesInGraphTxEvent(new UpdateTaskExecStatesInGraphTxEvent(task.execContextId, task.id));
        return status;
    }

}
