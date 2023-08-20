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
import ai.metaheuristic.ai.dispatcher.cache.CacheTxService;
import ai.metaheuristic.ai.dispatcher.cache.CacheUtils;
import ai.metaheuristic.ai.dispatcher.data.CacheData;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.events.FindUnassignedTasksAndRegisterInQueueTxEvent;
import ai.metaheuristic.ai.dispatcher.event.events.RegisterTaskForCheckCachingEvent;
import ai.metaheuristic.ai.dispatcher.event.events.TaskFinishWithErrorEvent;
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
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Serge
 * Date: 10/30/2020
 * Time: 7:10 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
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


    private final TaskCheckCachingTxService taskCheckCachingTxService;
    private final ExecContextReadinessStateService execContextReadinessStateService;
    private final CacheTxService cacheService;
    private final CacheProcessRepository cacheProcessRepository;
    private final ExecContextCache execContextCache;
    private final TaskRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);

    private final Set<Long> queueIds = new HashSet<>();
    private final LinkedList<RegisterTaskForCheckCachingEvent> queue = new LinkedList<>();

    private long mills = 0L;
    public boolean disableCacheChecking = false;

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public void putToQueue(final RegisterTaskForCheckCachingEvent event) {
        if (disableCacheChecking) {
            return;
        }
        writeLock.lock();
        try {
            // re-create queueIds, there is a possibility that queueIds was unsyncronized with queue when a task was resetting
            if (System.currentTimeMillis() - mills > 60_000) {
                queueIds.clear();
                queue.forEach(o->queueIds.add(o.taskId));
                mills = System.currentTimeMillis();
            }
            final long completedTaskCount = executor.getCompletedTaskCount();
            final long taskCount = executor.getTaskCount();
            final boolean contains = queueIds.contains(event.taskId);
            final boolean queueIsFull = (taskCount - completedTaskCount) > 1000;
            if (contains || queueIsFull) {
                log.debug("task #{} wasn't queued, contains: {}, queueIsFull: {}", event.taskId, contains, queueIsFull);
                return;
            }
            queue.add(event);
            queueIds.add(event.taskId);
        } finally {
            writeLock.unlock();
        }
        checkCaching();
    }

    @Nullable
    private RegisterTaskForCheckCachingEvent pullFromQueue() {
        writeLock.lock();
        try {
            final RegisterTaskForCheckCachingEvent task = queue.pollFirst();
            if (task==null) {
                if (!queueIds.isEmpty()) {
                    throw new IllegalStateException("(!queueIds.isEmpty())");
                }
                return null;
            }
            queueIds.remove(task.taskId);
            return task;
        } finally {
            writeLock.unlock();
        }
    }

    public void checkCaching() {
        final int activeCount = executor.getActiveCount();
        if (log.isDebugEnabled()) {
            final long completedTaskCount = executor.getCompletedTaskCount();
            final long taskCount = executor.getTaskCount();
            log.debug("checkCaching, active task in executor: {}, awaiting tasks: {}, queue size: {}", activeCount, taskCount - completedTaskCount, queue.size());
        }

        if (activeCount>0) {
            return;
        }
        executor.submit(() -> {
            RegisterTaskForCheckCachingEvent event;
            while ((event = pullFromQueue())!=null) {
                try {
                    checkCachingInternal(event);
                }
                catch (Throwable th) {
                    log.error("Error", th);
                    eventPublisher.publishEvent(new TaskFinishWithErrorEvent(event.taskId, "Error while checking cache for task #" +event.taskId+", error: " + th.getMessage()));
                    return;
                }
                finally {
                    eventPublisher.publishEvent(new FindUnassignedTasksAndRegisterInQueueTxEvent());
                }
            }
        });
    }

    private void checkCachingInternal(RegisterTaskForCheckCachingEvent event) {
        final boolean notReady = execContextReadinessStateService.isNotReady(event.execContextId);
        log.debug("execContextId: {}, notReady: {}", event.execContextId, notReady);
        if (notReady) {
            return;
        }

        ExecContextImpl execContext = execContextCache.findById(event.execContextId, true);
        if (execContext == null) {
            log.debug("Exec context not found, execContextId: {}", event.execContextId);
            return;
        }

        PrepareData prepareData = getCacheProcess(execContext.asSimple(), event.taskId);
        if (prepareData.state==PrepareDataState.none) {
            log.debug("execContextId: {}, task: {}, prepareData.state: PrepareDataState.none", event.execContextId, event.taskId);
            return;
        }

        try {
            log.debug("Start taskCheckCachingService.checkCaching(), execContextId: {}, task: {}", event.execContextId, event.taskId);
            TaskSyncService.getWithSyncVoid(event.taskId,
                    () -> taskCheckCachingTxService.checkCaching(event.execContextId, event.taskId, prepareData.cacheProcess));

        } catch (InvalidateCacheProcessException e) {
            log.error("#610.200 caught InvalidateCacheProcessException, {}", e.getMessage());
            try {
                TaskSyncService.getWithSyncVoid(e.taskId,
                        () -> taskCheckCachingTxService.invalidateCacheItemAndSetTaskToNone(e.execContextId, e.taskId, e.cacheProcessId));
            } catch (Throwable th) {
                log.error("#610.300 error while invalidating task #"+e.taskId, th);
            }
        }
    }

    public PrepareData getCacheProcess(ExecContextData.SimpleExecContext simpleExecContext, Long taskId) {
        TxUtils.checkTxNotExists();

        TaskImpl task = taskRepository.findByIdReadOnly(taskId);
        if (task==null) {
            log.debug("Task wasn't found, execContextId: {}, task: {}", simpleExecContext.execContextId, taskId);
            return PREPARE_DATA_NONE;
        }
        if (task.execState!=EnumsApi.TaskExecState.CHECK_CACHE.value) {
            log.info("#609.010 task #{} was already checked for cached variables", taskId);
            return PREPARE_DATA_NONE;
        }

        ExecContextParamsYaml ecpy = simpleExecContext.getParamsYaml();
        CacheData.SimpleKey key = getSimpleKey(ecpy, task);
        if (key==null) {
            return PREPARE_DATA_NONE;
        }

        log.debug("execContextId: {}, task: {}, let's try to find cacheProcess for key {}", simpleExecContext.execContextId, taskId, key);
        CacheProcess cacheProcess = cacheProcessRepository.findByKeySha256LengthReadOnly(key.key());

        log.debug("execContextId: {}, task: {}, CacheProcess: {}", simpleExecContext.execContextId, taskId, cacheProcess);
        return new PrepareData(cacheProcess, PrepareDataState.ok);
    }

    @Nullable
    public CacheData.SimpleKey getSimpleKey(ExecContextParamsYaml ecpy, TaskImpl task) {
        TaskParamsYaml tpy = task.getTaskParamsYaml();
        ExecContextParamsYaml.Process p = ecpy.findProcess(tpy.task.processCode);
        if (p==null) {
            log.warn("609.023 Process {} wasn't found", tpy.task.processCode);
            return null;
        }
        CacheData.FullKey fullKey;
        try {
            log.debug("start cacheService.getKey(), execContextId: {}, task: {}", task.execContextId, task.id);
            fullKey = cacheService.getKey(tpy, p.function);
        } catch (VariableCommonException e) {
            log.warn("609.025 ExecContext: #{}, VariableCommonException: {}", task.execContextId, e.getAdditionalInfo());
            return null;
        }
        log.debug("done cacheService.getKey(), execContextId: {}, task: {}", task.execContextId, task.id);

        CacheData.SimpleKey key = CacheUtils.fullKeyToSimpleKey(fullKey);
        return key;
    }

}
