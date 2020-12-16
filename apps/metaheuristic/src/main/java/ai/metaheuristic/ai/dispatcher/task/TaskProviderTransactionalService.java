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
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.event.ProcessDeletedExecContextEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStatusService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskFinishingService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * @author Serge
 * Date: 10/11/2020
 * Time: 3:25 PM
 */
@SuppressWarnings("DuplicatedCode")
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class TaskProviderTransactionalService {

    private final TaskRepository taskRepository;
    private final ExecContextTaskFinishingService execContextTaskFinishingService;
    private final ExecContextStatusService execContextStatusService;
    private final ExecContextService execContextService;

    @Data
    @AllArgsConstructor
    @EqualsAndHashCode(of = {"taskId"})
    public static class QueuedTask {
        public Long execContextId;
        public Long taskId;
        public TaskImpl task;
        public TaskParamsYaml taskParamYaml;
        public String tags;
        public int priority;
    }

    public static class AllocatedTask {
        public final QueuedTask queuedTask;
        public boolean assigned;

        public AllocatedTask(QueuedTask queuedTask) {
            this.queuedTask = queuedTask;
        }
    }

    private static final int GROUP_SIZE = 5;
    private static final int MIN_QUEUE_SIZE = 25;

    public static class TaskGroup {
        @Nullable
        public Long execContextId;

        public final AllocatedTask[] tasks = new AllocatedTask[GROUP_SIZE];
        public int allocated = 0;

        public TaskGroup(Long execContextId) {
            this.execContextId = execContextId;
        }

        public boolean alreadyRegistered(Long taskId) {
            if (execContextId==null) {
                return false;
            }
            for (AllocatedTask task : tasks) {
                if (task!=null && task.queuedTask.taskId.equals(taskId)) {
                    return true;
                }
            }
            return false;
        }

        public boolean deRegisterTask(Long taskId) {
            for (int i = 0; i < tasks.length; i++) {
                if (tasks[i]!=null && tasks[i].queuedTask.taskId.equals(taskId)) {
                    tasks[i] = null;
                    --allocated;
                    if (Arrays.stream(tasks).noneMatch(Objects::nonNull)) {
                        execContextId = null;
                    }
                    return true;
                }
            }
            return false;
        }

        public boolean isNewTask() {
            if (execContextId==null) {
                return false;
            }
            for (AllocatedTask task : tasks) {
                if (task != null && !task.assigned) {
                    return true;
                }
            }
            return false;
        }

        public void addTask(QueuedTask task) {
            if (allocated==GROUP_SIZE) {
                throw new IllegalStateException("already allocated");
            }
            ++allocated;
            for (int i = 0; i < tasks.length; i++) {
                if (tasks[i]==null) {
                    tasks[i] = new AllocatedTask(task);
                }
            }
        }

        public void reset() {
            allocated = 0;
            execContextId = null;
            Arrays.fill(tasks, null);
        }
    }

    private final AtomicInteger queuePtr = new AtomicInteger();
    private final CopyOnWriteArrayList<TaskGroup> taskGroups = new CopyOnWriteArrayList<>();

    private void addNewTask(QueuedTask task) {
        List<TaskGroup> temp = new ArrayList<>(taskGroups);
        temp.sort(Comparator.comparingInt(o -> o.allocated));
        TaskGroup taskGroup = null;
        for (TaskGroup group : temp) {
            if (task.execContextId.equals(group.execContextId)) {
                if (group.allocated<GROUP_SIZE) {
                    taskGroup = group;
                }
            }
        }
        if (taskGroup==null) {
            taskGroup = new TaskGroup(task.execContextId);
            taskGroups.add(taskGroup);
        }
        taskGroup.addTask(task);
    }

    private static class GroupIterator implements Iterator<QueuedTask> {

        private int step = 0;
        private int level = 0;
        private final CopyOnWriteArrayList<TaskGroup> taskGroups;
        private final AtomicInteger queuePtr;

        public GroupIterator(CopyOnWriteArrayList<TaskGroup> taskGroups, AtomicInteger queuePtr) {
            this.taskGroups = taskGroups;
            this.queuePtr = queuePtr;
        }

        @Override
        public boolean hasNext() {
            if (step>=taskGroups.size()) {
                if (level<GROUP_SIZE) {
                    level;
                    step = 0;
                }
                return false;
            }
            ++step;
            return taskGroups.get((step+queuePtr.get())%taskGroups.size()).ha;
        }

        @Override
        public QueuedTask next() {
            return null;
        }

    }

    private Iterator iterator() {
        return new Iterator() {
            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public Object next() {
                return null;
            }
        };
    }

    /**
     * this Map contains an AtomicLong which contains millisecond value which is specify how long to not use concrete processor
     * if there is any problem with such Processor. After cool-down period of time this processor can be used in processing.
     *
     * key - processorId
     * value - milliseconds when a processor was banned;
     */
    private final Map<Long, AtomicLong> bannedSince = new HashMap<>();

    public void processDeletedExecContext(ProcessDeletedExecContextEvent event) {
        List<TaskGroup> forRemoving = new ArrayList<>();
        for (TaskGroup taskGroup : taskGroups) {
            if (event.execContextId.equals(taskGroup.execContextId)) {
                if (taskGroups.size()-forRemoving.size()>MIN_QUEUE_SIZE) {
                    forRemoving.add(taskGroup);
                }
                else {
                    taskGroup.reset();
                }
            }
        }
        if (!forRemoving.isEmpty()) {
            taskGroups.removeAll(forRemoving);
        }
    }

    private boolean alreadyRegistered(Long taskId) {
        return taskGroups.stream().anyMatch(o->o.alreadyRegistered(taskId));
    }

    public void registerTask(Long execContextId, Long taskId) {
        if (alreadyRegistered(taskId)) {
            return;
        }

        ExecContextImpl ec =  execContextService.findById(execContextId);
        if (ec==null) {
            log.warn("#317.010 Can't register task #{}, execContext #{} doesn't exist", taskId, execContextId);
            return;
        }
        final ExecContextParamsYaml execContextParamsYaml = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(ec.params);

        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("#317.015 Can't register task #{}, task doesn't exist", taskId);
            return;
        }
        final TaskParamsYaml taskParamYaml;
        try {
            taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
        } catch (YAMLException e) {
            String es = S.f("#317.020 Task #%s has broken params yaml and will be skipped, error: %s, params:\n%s", task.getId(), e.toString(), task.getParams());
            log.error(es, e.getMessage());
            execContextTaskFinishingService.finishWithErrorWithTx(task.id, es);
            return;
        }

        ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(taskParamYaml.task.processCode);
        if (p==null) {
            log.warn("#317.025 Can't register task #{}, process {} doesn't exist in execContext #{}", taskId, taskParamYaml.task.processCode, execContextId);
            return;
        }

        final QueuedTask queuedTask = new QueuedTask(task.execContextId, taskId, task, taskParamYaml, p.tags, p.priority);
        addNewTask(queuedTask);
/*
        tasks.sort((o1, o2)->{
            if(o1.priority!=o2.priority) {
                // sort in descendant order;
                return Integer.compare(o2.priority, o1.priority);
            }
            return Long.compare(o1.taskId, o2.taskId);
        });
*/
    }

    public void deRegisterTask(Long execContextId, Long taskId) {
        for (TaskGroup taskGroup : taskGroups) {
            if (execContextId.equals(taskGroup.execContextId)) {
                if (taskGroup.deRegisterTask(taskId)) {
                    return;
                }
            }
        }
    }

    public boolean isQueueEmpty() {
        return taskGroups.stream().noneMatch(TaskGroup::isNewTask);
    }

    @Nullable
    @Transactional
    public TaskImpl findUnassignedTaskAndAssign(Processor processor, ProcessorStatusYaml psy, boolean isAcceptOnlySigned) {

        if (isQueueEmpty()) {
            return null;
        }

        AtomicLong longHolder = bannedSince.computeIfAbsent(processor.id, o -> new AtomicLong(0));
        if (longHolder.get() != 0 && System.currentTimeMillis() - longHolder.get() < TimeUnit.MINUTES.toMillis(30)) {
            return null;
        }

        TaskImpl resultTask = null;
        List<QueuedTask> forRemoving = new ArrayList<>();

        KeepAliveResponseParamYaml.ExecContextStatus statuses = execContextStatusService.getExecContextStatuses();
        try {
            for (QueuedTask queuedTask : tasks) {
                if (!statuses.isStarted(queuedTask.execContextId)) {
                    continue;
                }

                if (queuedTask.task.execState == EnumsApi.TaskExecState.IN_PROGRESS.value) {
                    // may be happened because of multi-threaded processing of internal function
                    forRemoving.add(queuedTask);
                    continue;
                }

                if (queuedTask.task.execState != EnumsApi.TaskExecState.NONE.value) {
                    log.warn("#317.040 Task #{} with function '{}' was already processed with status {}",
                            queuedTask.task.getId(), queuedTask.taskParamYaml.task.function.code, EnumsApi.TaskExecState.from(queuedTask.task.execState));
                    forRemoving.add(queuedTask);
                    continue;
                }

                // all tasks with internal function will be processed by scheduler
                if (queuedTask.taskParamYaml.task.context == EnumsApi.FunctionExecContext.internal) {
                    forRemoving.add(queuedTask);
                    continue;
                }

                // check of git availability
                if (TaskUtils.gitUnavailable(queuedTask.taskParamYaml.task, psy.gitStatusInfo.status != Enums.GitStatus.installed)) {
                    log.warn("#317.060 Can't assign task #{} to processor #{} because this processor doesn't correctly installed git, git status info: {}",
                            processor.id, queuedTask.task.getId(), psy.gitStatusInfo
                    );
                    continue;
                }

                if (psy.env==null) {
                    log.error("#317.070 Processor {} has empty env.yaml", processor.id);
                }

                // check of tags
                if (!CollectionUtils.checkTagAllowed(queuedTask.tags, psy.env==null ? null : psy.env.tags)) {
                    continue;
                }

                if (!S.b(queuedTask.taskParamYaml.task.function.env)) {
                    String interpreter = psy.env.getEnvs().get(queuedTask.taskParamYaml.task.function.env);
                    if (interpreter == null) {
                        log.warn("#317.080 Can't assign task #{} to processor #{} because this processor doesn't have defined interpreter for function's env {}",
                                queuedTask.task.getId(), processor.id, queuedTask.taskParamYaml.task.function.env
                        );
                        longHolder.set(System.currentTimeMillis());
                        continue;
                    }
                }

                final List<EnumsApi.OS> supportedOS = FunctionCoreUtils.getSupportedOS(queuedTask.taskParamYaml.task.function.metas);
                if (psy.os != null && !supportedOS.isEmpty() && !supportedOS.contains(psy.os)) {
                    log.info("#317.100 Can't assign task #{} to processor #{}, " +
                                    "because this processor doesn't support required OS version. processor: {}, function: {}",
                            processor.id, queuedTask.task.getId(), psy.os, supportedOS
                    );
                    longHolder.set(System.currentTimeMillis());
                    continue;
                }

                if (isAcceptOnlySigned) {
                    if (queuedTask.taskParamYaml.task.function.checksumMap == null || queuedTask.taskParamYaml.task.function.checksumMap.keySet().stream().noneMatch(o -> o.isSigned)) {
                        log.warn("#317.120 Function with code {} wasn't signed", queuedTask.taskParamYaml.task.function.getCode());
                        continue;
                    }
                }

                resultTask = queuedTask.task;
                // check that downgrading is supporting
                try {
                    TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(queuedTask.task.getParams());
                    //noinspection unused
                    String params = TaskParamsYamlUtils.BASE_YAML_UTILS.toStringAsVersion(tpy, psy.taskParamsVersion);
                } catch (DowngradeNotSupportedException e) {
                    log.warn("#317.140 Task #{} can't be assigned to processor #{} because it's too old, downgrade to required taskParams level {} isn't supported",
                            resultTask.getId(), processor.id, psy.taskParamsVersion);
                    longHolder.set(System.currentTimeMillis());
                    resultTask = null;
                }

                if (resultTask != null) {
                    break;
                }
            }
        }
        finally {
            tasks.removeAll(forRemoving);
        }

        if (resultTask == null) {
            return null;
        }

        TaskImpl t = taskRepository.findById(resultTask.id).orElse(null);
        if (t==null) {
            log.warn("#317.015 Can't assign task #{}, task doesn't exist", resultTask.id);
            return null;
        }
        // normal way of operation for this Processor
        longHolder.set(0);

        t.setAssignedOn(System.currentTimeMillis());
        t.setProcessorId(processor.id);
        t.setExecState(EnumsApi.TaskExecState.IN_PROGRESS.value);
        t.setResultResourceScheduledOn(0);

        return t;
    }

    public void deregisterTasksByExecContextId(Long execContextId) {
        List<QueuedTask> forRemoving = new ArrayList<>();
        for (QueuedTask queuedTask : tasks) {
            if (queuedTask.execContextId.equals(execContextId)) {
                forRemoving.add(queuedTask);
            }
        }
        tasks.removeAll(forRemoving);
    }
}
