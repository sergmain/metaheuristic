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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.CacheProcess;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.cache.CacheService;
import ai.metaheuristic.ai.dispatcher.data.CacheData;
import ai.metaheuristic.ai.dispatcher.event.RegisterTaskForCheckCachingEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextReadinessStateService;
import ai.metaheuristic.ai.dispatcher.repositories.CacheProcessRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.exceptions.InvalidateCacheProcessException;
import ai.metaheuristic.ai.exceptions.VariableCommonException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author Serge
 * Date: 10/30/2020
 * Time: 7:10 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskCheckCachingTopLevelService {

    public enum PrepareDataState {ok, none}

    @AllArgsConstructor
    public static class PrepareData {
        @Nullable
        public CacheProcess cacheProcess = null;
        public PrepareDataState state;

        public PrepareData(PrepareDataState state) {
            if (state!=PrepareDataState.none) {
                throw new IllegalStateException("(state!=PrepareDataState.none)");
            }
            this.state = state;
        }
    }

    private static final PrepareData PREPARE_DATA_NONE = new PrepareData(PrepareDataState.none);


    private final TaskCheckCachingService taskCheckCachingService;
    private final ExecContextReadinessStateService execContextReadinessStateService;
    private final CacheService cacheService;
    private final CacheProcessRepository cacheProcessRepository;
    private final ExecContextCache execContextCache;
    private final TaskRepository taskRepository;

    private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);

    private final Set<Long> queueIds = new HashSet<>();
    private final LinkedList<RegisterTaskForCheckCachingEvent> queue = new LinkedList<>();

    public void putToQueue(final RegisterTaskForCheckCachingEvent event) {
        synchronized (queue) {
            final long completedTaskCount = executor.getCompletedTaskCount();
            final long taskCount = executor.getTaskCount();
            if (queueIds.contains(event.taskId) || (taskCount - completedTaskCount)  >100) {
                return;
            }
            queue.add(event);
            queueIds.add(event.taskId);
        }
    }

    @Nullable
    private RegisterTaskForCheckCachingEvent pullFromQueue() {
        synchronized (queue) {
            final RegisterTaskForCheckCachingEvent task = queue.pollFirst();
            if (task==null) {
                if (!queueIds.isEmpty()) {
                    throw new IllegalStateException("(!queueIds.isEmpty())");
                }
                return null;
            }
            queueIds.remove(task.taskId);
            return task;
        }
    }

    public void checkCaching() {
        final int activeCount = executor.getActiveCount();
        final long completedTaskCount = executor.getCompletedTaskCount();
        final long taskCount = executor.getTaskCount();

        log.debug("checkCaching, active task in executor: {}, awaiting tasks: {}", activeCount, taskCount - completedTaskCount);
        if (activeCount>0) {
            return;
        }
        executor.submit(() -> {
            RegisterTaskForCheckCachingEvent event;
            while ((event = pullFromQueue())!=null) {
                checkCachingInternal(event);
            }
        });
    }


    private void checkCachingInternal(RegisterTaskForCheckCachingEvent event) {
        final boolean notReady = execContextReadinessStateService.isNotReady(event.execContextId);
        log.debug("execContextId: {}, notReady: {}", event.execContextId, notReady);
        if (notReady) {
            return;
        }

        ExecContextImpl execContext = execContextCache.findById(event.execContextId);
        if (execContext == null) {
            log.debug("Exec context not found, execContextId: {}", event.execContextId);
            return;
        }

        PrepareData prepareData = getCacheProcess(execContext, event.taskId);
        if (prepareData.state==PrepareDataState.none) {
            log.debug("execContextId: {}, task: {}, prepareData.state: PrepareDataState.none", event.execContextId, event.taskId);
            return;
        }

        try {
            log.debug("Start taskCheckCachingService.checkCaching(), execContextId: {}, task: {}", event.execContextId, event.taskId);
            TaskSyncService.getWithSyncVoid(event.taskId,
                    () -> taskCheckCachingService.checkCaching(event.execContextId, event.taskId, prepareData.cacheProcess));

        } catch (InvalidateCacheProcessException e) {
            log.error("#610.200 caught InvalidateCacheProcessException, {}", e.getMessage());
            try {
                TaskSyncService.getWithSyncVoid(e.taskId,
                        () -> taskCheckCachingService.invalidateCacheItemAndSetTaskToNone(e.execContextId, e.taskId, e.cacheProcessId));
            } catch (Throwable th) {
                log.error("#610.300 error while invalidating task #"+e.taskId, th);
            }
        }
    }

    private PrepareData getCacheProcess(ExecContextImpl execContext, Long taskId) {
        TxUtils.checkTxNotExists();

        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.debug("Task wasn't found, execContextId: {}, task: {}", execContext.id, taskId);
            return PREPARE_DATA_NONE;
        }
        if (task.execState!=EnumsApi.TaskExecState.CHECK_CACHE.value) {
            log.info("#609.010 task #{} was already checked for cached variables", taskId);
            return PREPARE_DATA_NONE;
        }

        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
        ExecContextParamsYaml ecpy = execContext.getExecContextParamsYaml();
        ExecContextParamsYaml.Process p = ecpy.findProcess(tpy.task.processCode);
        if (p==null) {
            log.warn("609.023 Process {} wasn't found", tpy.task.processCode);
            return PREPARE_DATA_NONE;
        }
        CacheData.Key fullKey;
        try {
            log.debug("start cacheService.getKey(), execContextId: {}, task: {}", execContext.id, taskId);
            fullKey = cacheService.getKey(tpy, p.function);
        } catch (VariableCommonException e) {
            log.warn("#609.025 ExecContext: #{}, VariableCommonException: {}", execContext.id, e.getAdditionalInfo());
            return PREPARE_DATA_NONE;
        }
        log.debug("done cacheService.getKey(), execContextId: {}, task: {}", execContext.id, taskId);


        String keyAsStr = fullKey.asString();
        byte[] bytes = keyAsStr.getBytes();

        CacheProcess cacheProcess=null;
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            String sha256 = Checksum.getChecksum(EnumsApi.HashAlgo.SHA256, is);
            String key = new CacheData.Sha256PlusLength(sha256, keyAsStr.length()).asString();

            log.debug("execContextId: {}, task: {}, let's try to find cacheProcess for key {}", execContext.id, taskId, key);
            cacheProcess = cacheProcessRepository.findByKeySha256LengthReadOnly(key);
        } catch (IOException e) {
            log.error("#609.040 Error while preparing a cache key, task will be processed without cached data", e);
        }
        log.debug("execContextId: {}, task: {}, CacheProcess: {}", execContext.id, taskId, cacheProcess);
        return new PrepareData(cacheProcess, PrepareDataState.ok);
    }

}
