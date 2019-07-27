/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.workbook;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.comm.Protocol;
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.launchpad.beans.Station;
import ai.metaheuristic.ai.launchpad.beans.TaskImpl;
import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.binary_data.SimpleCodeAndStorageUrl;
import ai.metaheuristic.ai.launchpad.experiment.task.SimpleTaskExecResult;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.station.StationCache;
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.utils.holders.LongHolder;
import ai.metaheuristic.ai.yaml.station_status.StationStatus;
import ai.metaheuristic.ai.yaml.station_status.StationStatusUtils;
import ai.metaheuristic.ai.yaml.workbook.WorkbookParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.plan.PlanApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.api.launchpad.Plan;
import ai.metaheuristic.api.launchpad.Task;
import ai.metaheuristic.api.launchpad.Workbook;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@Profile("launchpad")
@Slf4j
@RequiredArgsConstructor
public class WorkbookService {

    private static final TasksAndAssignToStationResult EMPTY_RESULT = new TasksAndAssignToStationResult(null);

    private final Globals globals;
    private final WorkbookRepository workbookRepository;
    private final PlanCache planCache;
    private final BinaryDataService binaryDataService;
    private final TaskRepository taskRepository;
    private final TaskPersistencer taskPersistencer;
    private final StationCache stationCache;
    private final WorkbookCache workbookCache;
    private final WorkbookGraphService workbookGraphService;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TasksAndAssignToStationResult {
        Protocol.AssignedTask.Task simpleTask;
    }

    public OperationStatusRest resetTask(long taskId) {
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#705.010 Can't re-run task "+taskId+", task with such taskId wasn't found");
        }
        WorkbookImpl workbook = workbookRepository.findByIdForUpdate(task.getWorkbookId());
        if (workbook == null) {
            taskPersistencer.finishTaskAsBrokenOrError(taskId, EnumsApi.TaskExecState.BROKEN);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#705.020 Can't re-run task "+taskId+", this task is orphan and doesn't belong to any workbook");
        }

        Task t = taskPersistencer.resetTask(task);
        if (t==null) {
            WorkbookOperationStatusWithTaskList withTaskList = updateGraphWithSettingAllChildrenTasksAsBroken(workbook, task.id);
            updateTasksStateInDb(withTaskList);
            if (withTaskList.status.status== EnumsApi.OperationStatus.ERROR) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#705.030 Can't re-run task #" + taskId + ", see log for more information");
            }
        }
        else {
            WorkbookOperationStatusWithTaskList withTaskList = updateGraphWithResettingAllChildrenTasks(workbook, task.id);
            if (withTaskList.status.status== EnumsApi.OperationStatus.ERROR) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#705.040 Can't re-run task #" + taskId + ", see log for more information");
            }
            updateTasksStateInDb(withTaskList);

            if (workbook.execState==EnumsApi.WorkbookExecState.FINISHED.code) {
                toState(workbook.id, EnumsApi.WorkbookExecState.STARTED);
            }
        }

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public static class WorkbookDeletionEvent extends ApplicationEvent {
        public long workbookId;

        /**
         * Create a new ApplicationEvent.
         *
         * @param source the object on which the event initially occurred (never {@code null})
         */
        public WorkbookDeletionEvent(Object source, long workbookId) {
            super(source);
            this.workbookId = workbookId;
        }
    }

    @EqualsAndHashCode(of = "workbookId")
    public static class WorkbookDeletionListener implements ApplicationListener<WorkbookDeletionEvent> {
        private long workbookId;

        private Consumer<Long> consumer;

        public WorkbookDeletionListener(long workbookId, Consumer<Long> consumer) {
            this.workbookId = workbookId;
            this.consumer = consumer;
        }

        @Override
        public void onApplicationEvent( WorkbookDeletionEvent event) {
            consumer.accept(event.workbookId);
        }
    }

    public void toProduced(Long workbookId) {
        toState(workbookId, EnumsApi.WorkbookExecState.PRODUCED);
    }

    public void toState(Long workbookId, EnumsApi.WorkbookExecState state) {
        WorkbookImpl workbook = workbookRepository.findByIdForUpdate(workbookId);
        if (workbook==null) {
            String es = "#705.082 Can't change exec state to "+state+" for workbook #" + workbookId;
            log.error(es);
            throw new IllegalStateException(es);
        }
        workbook.setExecState(state.code);
        workbookCache.save(workbook);
    }

    public void toFinished(Long workbookId) {
        toStateWithCompletion(workbookId, EnumsApi.WorkbookExecState.FINISHED);
    }

    public void toError(Long workbookId) {
        toStateWithCompletion(workbookId, EnumsApi.WorkbookExecState.ERROR);
    }

    public void toStateWithCompletion(Long workbookId, EnumsApi.WorkbookExecState state) {
        WorkbookImpl workbook = workbookRepository.findByIdForUpdate(workbookId);
        if (workbook==null) {
            String es = "#705.080 Can't change exec state to "+state+" for workbook #" + workbookId;
            log.error(es);
            throw new IllegalStateException(es);
        }
        workbook.setCompletedOn(System.currentTimeMillis());
        workbook.setExecState(state.code);
        workbookCache.save(workbook);
    }

    public PlanApiData.TaskProducingResultComplex createWorkbook(Long planId, WorkbookParamsYaml params) {
        PlanApiData.TaskProducingResultComplex result = new PlanApiData.TaskProducingResultComplex();

        WorkbookImpl wb = new WorkbookImpl();
        wb.setPlanId(planId);
        wb.setCreatedOn(System.currentTimeMillis());
        wb.setExecState(EnumsApi.WorkbookExecState.NONE.code);
        wb.setCompletedOn(null);
        params.graph = WorkbookGraphService.EMPTY_GRAPH;
        wb.updateParams(params);
        wb.setValid(true);

        WorkbookParamsYaml resourceParam = wb.getWorkbookParamsYaml();
        List<SimpleCodeAndStorageUrl> inputResourceCodes = binaryDataService.getResourceCodesInPool(resourceParam.getAllPoolCodes());
        if (inputResourceCodes==null || inputResourceCodes.isEmpty()) {
            result.planProducingStatus = EnumsApi.PlanProducingStatus.INPUT_POOL_CODE_DOESNT_EXIST_ERROR;
            return result;
        }

        workbookCache.save(wb);
        result.planProducingStatus = EnumsApi.PlanProducingStatus.OK;
        result.workbook = wb;

        return result;
    }

    public void toStopped(long workbookId) {
        WorkbookImpl wb = workbookRepository.findByIdForUpdate(workbookId);
        if (wb==null) {
            return;
        }
        wb.setExecState(EnumsApi.WorkbookExecState.STOPPED.code);
        workbookCache.save(wb);
    }

    public void changeValidStatus(Long workbookId, boolean status) {
        WorkbookImpl workbook = workbookRepository.findByIdForUpdate(workbookId);
        if (workbook==null) {
            return;
        }
        workbook.setValid(status);
        workbookCache.save(workbook);
    }

    public EnumsApi.PlanProducingStatus toProducing(Long workbookId) {
        WorkbookImpl wb = workbookRepository.findByIdForUpdate(workbookId);
        if (wb==null) {
            return EnumsApi.PlanProducingStatus.WORKBOOK_NOT_FOUND_ERROR;
        }
        wb.setExecState(EnumsApi.WorkbookExecState.PRODUCING.code);
        workbookCache.save(wb);
        return EnumsApi.PlanProducingStatus.OK;
    }

    public PlanApiData.WorkbookResult getWorkbookExtended(Long workbookId) {
        if (workbookId==null) {
            return new PlanApiData.WorkbookResult("#705.090 workbookId is null");
        }
        WorkbookImpl workbook = workbookCache.findById(workbookId);
        if (workbook == null) {
            return new PlanApiData.WorkbookResult("#705.100 workbook wasn't found, workbookId: " + workbookId);
        }
        PlanImpl plan = planCache.findById(workbook.getPlanId());
        if (plan == null) {
            return new PlanApiData.WorkbookResult("#705.110 plan wasn't found, planId: " + workbook.getPlanId());
        }

        if (!plan.getId().equals(workbook.getPlanId())) {
            workbook = workbookRepository.findByIdForUpdate(workbookId);
            workbook.setValid(false);
            workbookRepository.save(workbook);
            return new PlanApiData.WorkbookResult("#705.120 planId doesn't match to workbook.planId, planId: " + workbook.getPlanId()+", workbook.planId: " + workbook.getPlanId());
        }

        //noinspection UnnecessaryLocalVariable
        PlanApiData.WorkbookResult result = new PlanApiData.WorkbookResult(plan, workbook);
        return result;
    }

    public PlanApiData.WorkbooksResult getWorkbooksOrderByCreatedOnDescResult(@PathVariable Long id, @PageableDefault(size = 5) Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.workbookRowsLimit, pageable);
        PlanApiData.WorkbooksResult result = new PlanApiData.WorkbooksResult();
        result.instances = workbookRepository.findByPlanIdOrderByCreatedOnDesc(pageable, id);
        result.currentPlanId = id;

        for (Workbook workbook : result.instances) {
            WorkbookParamsYaml wpy = WorkbookParamsYamlUtils.BASE_YAML_UTILS.to(workbook.getParams());
            wpy.graph = null;
            workbook.setParams( WorkbookParamsYamlUtils.BASE_YAML_UTILS.toString(wpy) );
            Plan plan = planCache.findById(workbook.getPlanId());
            if (plan==null) {
                log.warn("#705.130 Found workbook with wrong planId. planId: {}", workbook.getPlanId());
                continue;
            }
            result.plans.put(workbook.getId(), plan);
        }
        return result;
    }

    public synchronized TasksAndAssignToStationResult getTaskAndAssignToStation(long stationId, boolean isAcceptOnlySigned, Long workbookId) {

        final Station station = stationCache.findById(stationId);
        if (station == null) {
            log.error("#705.140 Station wasn't found for id: {}", stationId);
            return EMPTY_RESULT;
        }
        StationStatus ss;
        try {
            ss = StationStatusUtils.to(station.status);
        } catch (Throwable e) {
            log.error("#705.150 Error parsing current status of station:\n{}", station.status);
            log.error("#705.151 Error ", e);
            return EMPTY_RESULT;
        }
        if (ss.taskParamsVersion < TaskParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion()) {
            // this station is blacklisted. ignore it
            return EMPTY_RESULT;
        }

        List<Long> anyTaskId = taskRepository.findAnyActiveForStationId(Consts.PAGE_REQUEST_1_REC, stationId);
        if (!anyTaskId.isEmpty()) {
            // this station already has active task
            log.info("#705.160 can't assign any new task to the station #{} because this station has an active task #{}", stationId, anyTaskId);
            return EMPTY_RESULT;
        }

        List<Long> workbookIds;
        if (workbookId==null) {
            workbookIds = workbookRepository.findByExecStateOrderByCreatedOnAsc(EnumsApi.WorkbookExecState.STARTED.code);
        }
        else {
            WorkbookImpl workbook = workbookCache.findById(workbookId);
            if (workbook==null) {
                log.warn("#705.170 Workbook wasn't found for id: {}", workbookId);
                return EMPTY_RESULT;
            }
            if (workbook.getExecState()!= EnumsApi.WorkbookExecState.STARTED.code) {
                log.warn("#705.180 Workbook wasn't started. Current exec state: {}", EnumsApi.WorkbookExecState.toState(workbook.getExecState()));
                return EMPTY_RESULT;
            }
            workbookIds = List.of(workbook.id);
        }

        for (long wbId : workbookIds) {
            TasksAndAssignToStationResult result = findUnassignedTaskAndAssign(wbId, station, isAcceptOnlySigned);
            if (!result.equals(EMPTY_RESULT)) {
                return result;
            }
        }
        return EMPTY_RESULT;
    }

    private final Map<Long, LongHolder> bannedSince = new HashMap<>();

    public static List<Long> getIdsForSearch(List<WorkbookParamsYaml.TaskVertex> vertices, int page, int pageSize) {
        final int fromIndex = page * pageSize;
        if (vertices.size()== fromIndex) {
            return List.of();
        }
        int toIndex = fromIndex + (vertices.size()-pageSize>=fromIndex ? pageSize : vertices.size() - fromIndex);
        return vertices.subList(fromIndex, toIndex).stream()
                .map(v -> v.taskId)
                .collect(Collectors.toList());
    }

    private TasksAndAssignToStationResult findUnassignedTaskAndAssign(Long workbookId, Station station, boolean isAcceptOnlySigned) {

        LongHolder longHolder = bannedSince.computeIfAbsent(station.getId(), o -> new LongHolder(0));
        if (longHolder.value!=0 && System.currentTimeMillis() - longHolder.value < TimeUnit.MINUTES.toMillis(30)) {
            return EMPTY_RESULT;
        }
        WorkbookImpl workbook = workbookCache.findById(workbookId);
        if (workbook==null) {
            return EMPTY_RESULT;
        }
        List<WorkbookParamsYaml.TaskVertex> vertices = findAllForAssigning(workbookRepository.findByIdForUpdate(workbook.id));

        int page = 0;
        Task resultTask = null;
        List<Task> tasks;
        while ((tasks= getAllByStationIdIsNullAndWorkbookIdAndIdIn(workbookId, vertices, page++)).size()>0) {
            for (Task task : tasks) {
                final TaskParamsYaml taskParamYaml;
                try {
                    taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
                }
                catch (YAMLException e) {
                    log.error("#705.190 Task #{} has broken params yaml and will be skipped, error: {}, params:\n{}", task.getId(), e.toString(),task.getParams());
                    taskPersistencer.finishTaskAsBrokenOrError(task.getId(), EnumsApi.TaskExecState.BROKEN);
                    continue;
                }
                catch (Exception e) {
                    throw new RuntimeException("#705.200 Error", e);
                }

                StationStatus stationStatus = StationStatusUtils.to(station.status);
                if (taskParamYaml.taskYaml.snippet.sourcing== EnumsApi.SnippetSourcing.git &&
                        stationStatus.gitStatusInfo.status!= Enums.GitStatus.installed) {
                    log.warn("#705.210 Can't assign task #{} to station #{} because this station doesn't correctly installed git, git status info: {}",
                            station.getId(), task.getId(), stationStatus.gitStatusInfo
                    );
                    longHolder.value = System.currentTimeMillis();
                    continue;
                }

                if (taskParamYaml.taskYaml.snippet.env!=null) {
                    String interpreter = stationStatus.env.getEnvs().get(taskParamYaml.taskYaml.snippet.env);
                    if (interpreter == null) {
                        log.warn("#705.210 Can't assign task #{} to station #{} because this station doesn't have defined interpreter for snippet's env {}",
                                station.getId(), task.getId(), taskParamYaml.taskYaml.snippet.env
                        );
                        longHolder.value = System.currentTimeMillis();
                        continue;
                    }
                }

                if (isAcceptOnlySigned) {
                    if (!taskParamYaml.taskYaml.snippet.info.isSigned()) {
                        log.warn("#705.220 Snippet with code {} wasn't signed", taskParamYaml.taskYaml.snippet.getCode());
                        continue;
                    }
                    resultTask = task;
                    break;
                } else {
                    resultTask = task;
                    break;
                }
            }
            if (resultTask!=null) {
                break;
            }
        }
        if (resultTask==null) {
            return EMPTY_RESULT;
        }

        // normal way of operation
        longHolder.value = 0;

        Protocol.AssignedTask.Task assignedTask = new Protocol.AssignedTask.Task();
        assignedTask.setTaskId(resultTask.getId());
        assignedTask.setWorkbookId(workbookId);
        assignedTask.setParams(resultTask.getParams());

        resultTask.setAssignedOn(System.currentTimeMillis());
        resultTask.setStationId(station.getId());
        resultTask.setExecState(EnumsApi.TaskExecState.IN_PROGRESS.value);
        resultTask.setResultResourceScheduledOn(0);

        taskRepository.save((TaskImpl)resultTask);
        updateTaskExecStateByWorkbookId(workbookId, resultTask.getId(), EnumsApi.TaskExecState.IN_PROGRESS.value);

        return new TasksAndAssignToStationResult(assignedTask);
    }

    private List<Task> getAllByStationIdIsNullAndWorkbookIdAndIdIn(Long workbookId, List<WorkbookParamsYaml.TaskVertex> vertices, int page) {
        final List<Long> idsForSearch = getIdsForSearch(vertices, page, 20);
        if (idsForSearch.isEmpty()) {
            return List.of();
        }
        return taskRepository.findForAssigning(workbookId, idsForSearch);
    }

    public List<Long> storeAllConsoleResults(List<SimpleTaskExecResult> results) {
        final TaskPersistencer.PostTaskCreationAction action = t -> {
            if (t!=null) {
                updateTaskExecStateByWorkbookId(t.getWorkbookId(), t.getId(), t.getExecState());
            }
        };

        List<Long> ids = new ArrayList<>();
        for (SimpleTaskExecResult result : results) {
            ids.add(result.taskId);
            taskPersistencer.storeExecResult(result, action);
        }
        return ids;
    }

    private static final ConcurrentHashMap<Long, Object> syncMap = new ConcurrentHashMap<>(1000, 0.75f, 10);

    // workbook graph methods

    private OperationStatusRest updateTaskExecStateByWorkbookId(Long workbookId, Long taskId, int execState) {
        final Object obj = syncMap.computeIfAbsent(workbookId, o -> new Object());
        log.debug("Before entering in sync block, updateTaskExecStateInternal()");
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                WorkbookImpl workbook = workbookRepository.findByIdForUpdate(workbookId);
                final WorkbookOperationStatusWithTaskList status = updateTaskExecStateWithoutSync(workbook, taskId, execState);
                return status.status;
            } finally {
                syncMap.remove(workbookId);
            }
        }
    }

    public OperationStatusRest updateTaskExecState(WorkbookImpl workbook, Long taskId, int execState) {
        final Object obj = syncMap.computeIfAbsent(workbook.getId(), o -> new Object());
        log.debug("Before entering in sync block, updateTaskExecState()");
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                final WorkbookOperationStatusWithTaskList status = updateTaskExecStateWithoutSync(workbook, taskId, execState);
                return status.status;
            } finally {
                syncMap.remove(workbook.getId());
            }
        }
    }

    private WorkbookOperationStatusWithTaskList updateTaskExecStateWithoutSync(WorkbookImpl workbook, Long taskId, int execState) {
        changeTaskState(taskId, EnumsApi.TaskExecState.from(execState));
        final WorkbookOperationStatusWithTaskList status = workbookGraphService.updateTaskExecState(workbook, taskId, execState);
        updateTasksStateInDb(status);
        return status;
    }

    public List<WorkbookParamsYaml.TaskVertex> findAll(WorkbookImpl workbook) {
        final Object obj = syncMap.computeIfAbsent(workbook.getId(), o -> new Object());
        log.debug("Before entering in sync block, findAll()");
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                return workbookGraphService.findAll(workbook);
            } finally {
                syncMap.remove(workbook.getId());
            }
        }
    }

    public WorkbookOperationStatusWithTaskList updateGraphWithSettingAllChildrenTasksAsBroken(WorkbookImpl workbook, Long taskId) {
        final Object obj = syncMap.computeIfAbsent(workbook.getId(), o -> new Object());
        log.debug("Before entering in sync block, updateGraphWithInvalidatingAllChildrenTasks()");
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                return workbookGraphService.updateGraphWithSettingAllChildrenTasksAsBroken(workbook, taskId);
            } finally {
                syncMap.remove(workbook.getId());
            }
        }
    }

    public OperationStatusRest addNewTasksToGraph(WorkbookImpl workbook, List<Long> parentTaskIds, List<Long> taskIds) {
        if (workbook==null || workbook.getId()==null) {
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        final Object obj = syncMap.computeIfAbsent(workbook.getId(), o -> new Object());
//        log.debug("Before entering in sync block, addNewTasksToGraph()");
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                return workbookGraphService.addNewTasksToGraph(workbook, parentTaskIds, taskIds);
            } finally {
                syncMap.remove(workbook.getId());
            }
        }
    }

    public List<WorkbookParamsYaml.TaskVertex> findLeafs(WorkbookImpl workbook) {
        final Object obj = syncMap.computeIfAbsent(workbook.getId(), o -> new Object());
        log.debug("Before entering in sync block, findLeafs()");
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                return workbookGraphService.findLeafs(workbook);
            } finally {
                syncMap.remove(workbook.getId());
            }
        }
    }

    public Set<WorkbookParamsYaml.TaskVertex> findDescendants(WorkbookImpl workbook, Long taskId) {
        final Object obj = syncMap.computeIfAbsent(workbook.getId(), o -> new Object());
        log.debug("Before entering in sync block, findLeafs()");
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                return workbookGraphService.findDescendants(workbook, taskId);
            } finally {
                syncMap.remove(workbook.getId());
            }
        }
    }

    public long getCountUnfinishedTasks(WorkbookImpl workbook) {
        final Object obj = syncMap.computeIfAbsent(workbook.getId(), o -> new Object());
        log.debug("Before entering in sync block, getCountUnfinishedTasks()");
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                return workbookGraphService.getCountUnfinishedTasks(workbook);
            } finally {
                syncMap.remove(workbook.getId());
            }
        }
    }

    public WorkbookOperationStatusWithTaskList updateGraphWithResettingAllChildrenTasks(WorkbookImpl workbook, Long taskId) {
        final Object obj = syncMap.computeIfAbsent(workbook.getId(), o -> new Object());
        log.debug("Before entering in sync block, updateGraphWithResettingAllChildrenTasks()");
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                return workbookGraphService.updateGraphWithResettingAllChildrenTasks(workbook, taskId);
            } finally {
                syncMap.remove(workbook.getId());
            }
        }
    }

    public List<WorkbookParamsYaml.TaskVertex> findAllForAssigning(WorkbookImpl workbook) {
        final Object obj = syncMap.computeIfAbsent(workbook.getId(), o -> new Object());
        log.debug("Before entering in sync block, findAll()");
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                return workbookGraphService.findAllForAssigning(workbook);
            } finally {
                syncMap.remove(workbook.getId());
            }
        }
    }

    public WorkbookOperationStatusWithTaskList updateTaskExecStates(WorkbookImpl wb, ConcurrentHashMap<Long, Integer> taskStates) {
        if (taskStates==null || taskStates.isEmpty()) {
            return new WorkbookOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);
        }
        final Object obj = syncMap.computeIfAbsent(wb.getId(), o -> new Object());
        log.debug("Before entering in sync block, updateTaskExecStates()");
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                WorkbookImpl workbook = workbookRepository.findByIdForUpdate(wb.id);
                final WorkbookOperationStatusWithTaskList status = workbookGraphService.updateTaskExecStates(workbook, taskStates);
                updateTasksStateInDb(status);
                return status;
            } finally {
                syncMap.remove(wb.getId());
            }
        }
    }

    private void updateTasksStateInDb(WorkbookOperationStatusWithTaskList status) {
        status.childrenTasks.forEach(t -> {
            TaskImpl task = taskRepository.findById(t.taskId).orElse(null);
            if (task != null) {
                if (task.execState != t.execState.value) {
                    changeTaskState(task.id, t.execState);
                }
            } else {
                log.error("Graph state is compromised, found task in graph but it doesn't exist in db");
            }
        });
    }

    private void changeTaskState(Long taskId, EnumsApi.TaskExecState state){
        switch (state) {
            case NONE:
                taskPersistencer.resetTask(taskId);
                break;
            case BROKEN:
            case ERROR:
                taskPersistencer.finishTaskAsBrokenOrError(taskId, state);
                break;
            case OK:
                taskPersistencer.toOkSimple(taskId);
                break;
            case IN_PROGRESS:
                taskPersistencer.toInProgressSimple(taskId);
                break;
            default:
                throw new IllegalStateException("Right now it must be initialized somewhere else. state: " + state);
        }
    }



}
