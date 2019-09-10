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
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.launchpad.beans.Station;
import ai.metaheuristic.ai.launchpad.beans.TaskImpl;
import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.binary_data.SimpleCodeAndStorageUrl;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.station.StationCache;
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.utils.holders.LongHolder;
import ai.metaheuristic.ai.yaml.communication.launchpad.LaunchpadCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYaml;
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
import ai.metaheuristic.commons.utils.SnippetCoreUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
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
@SuppressWarnings("UnusedReturnValue")
public class WorkbookService {

    private final Globals globals;
    private final WorkbookRepository workbookRepository;
    private final PlanCache planCache;
    private final BinaryDataService binaryDataService;
    private final TaskRepository taskRepository;
    private final TaskPersistencer taskPersistencer;
    private final StationCache stationCache;
    private final WorkbookCache workbookCache;
    private final WorkbookGraphService workbookGraphService;
    private final WorkbookSyncService workbookSyncService;

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

    public OperationStatusRest resetBrokenTasks(Long workbookId) {
        final WorkbookImpl workbook = workbookCache.findById(workbookId);
        if (workbook==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#705.003 Can't find workbook with id #"+workbookId);
        }
        List<WorkbookParamsYaml.TaskVertex> vertices = findAllBroken(workbook);
        if (vertices==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#705.005 Can't find workbook with id #"+workbookId);
        }
        for (WorkbookParamsYaml.TaskVertex vertex : vertices) {
            resetTask(vertex.taskId);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest resetTask(Long taskId) {
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#705.010 Can't re-run task "+taskId+", task with such taskId wasn't found");
        }
        Task t = taskPersistencer.resetTask(task.id);
        if (t==null) {
            WorkbookOperationStatusWithTaskList withTaskList = updateGraphWithSettingAllChildrenTasksAsBroken(task.getWorkbookId(), task.id);
            updateTasksStateInDb(withTaskList);
            if (withTaskList.status.status== EnumsApi.OperationStatus.ERROR) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#705.030 Can't re-run task #" + taskId + ", see log for more information");
            }
        }
        else {
            WorkbookOperationStatusWithTaskList withTaskList = updateGraphWithResettingAllChildrenTasks(task.workbookId, task.id);
            if (withTaskList == null) {
                taskPersistencer.finishTaskAsBrokenOrError(taskId, EnumsApi.TaskExecState.BROKEN);
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#705.020 Can't re-run task "+taskId+", this task is orphan and doesn't belong to any workbook");
            }

            if (withTaskList.status.status== EnumsApi.OperationStatus.ERROR) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#705.040 Can't re-run task #" + taskId + ", see log for more information");
            }
            updateTasksStateInDb(withTaskList);

            WorkbookImpl workbook = workbookCache.findById(task.workbookId);
            if (workbook.execState != EnumsApi.WorkbookExecState.STARTED.code) {
                toState(workbook.id, EnumsApi.WorkbookExecState.STARTED);
            }
        }

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public Void toState(Long workbookId, EnumsApi.WorkbookExecState state) {
        return workbookSyncService.getWithSync(workbookId, workbook -> {
            if (workbook.execState!=state.code) {
                workbook.setExecState(state.code);
                workbookCache.save(workbook);
            }
            return null;
        });
    }

    public long getCountUnfinishedTasks(WorkbookImpl workbook) {
        return getCountUnfinishedTasks(workbook.id);
    }

    public Long getCountUnfinishedTasks(Long workbookId) {
        return workbookSyncService.getWithSync(workbookId, workbookGraphService::getCountUnfinishedTasks);
    }

    public List<WorkbookParamsYaml.TaskVertex> findAllVertices(Long workbookId) {
        return workbookSyncService.getWithSync(workbookId, workbookGraphService::findAll);
    }

    private Void toStateWithCompletion(Long workbookId, EnumsApi.WorkbookExecState state) {
        return workbookSyncService.getWithSync(workbookId, workbook -> {
            if (workbook.execState!=state.code) {
                workbook.setCompletedOn(System.currentTimeMillis());
                workbook.setExecState(state.code);
                workbookCache.save(workbook);
            }
            return null;
        });
    }

    public void toStopped(Long workbookId) {
        toState(workbookId, EnumsApi.WorkbookExecState.STOPPED);
    }

    public void toStarted(Long workbookId) {
        toState(workbookId, EnumsApi.WorkbookExecState.STARTED);
    }

    public void toProduced(Long workbookId) {
        toState(workbookId, EnumsApi.WorkbookExecState.PRODUCED);
    }

    public void toFinished(Long workbookId) {
        toStateWithCompletion(workbookId, EnumsApi.WorkbookExecState.FINISHED);
    }

    public void toExportingToAtlas(Long workbookId) {
        toStateWithCompletion(workbookId, EnumsApi.WorkbookExecState.EXPORTING_TO_ATLAS);
    }

    public void toExportingToAtlasStarted(Long workbookId) {
        toStateWithCompletion(workbookId, EnumsApi.WorkbookExecState.EXPORTING_TO_ATLAS_WAS_STARTED);
    }

    public void toError(Long workbookId) {
        toStateWithCompletion(workbookId, EnumsApi.WorkbookExecState.ERROR);
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

    public Void changeValidStatus(Long workbookId, boolean status) {
        return workbookSyncService.getWithSync(workbookId, workbook -> {
            workbook.setValid(status);
            workbookCache.save(workbook);
            return null;
        });
    }

    public EnumsApi.PlanProducingStatus toProducing(Long workbookId) {
        return workbookSyncService.getWithSync(workbookId, workbook -> {
            if (workbook.execState == EnumsApi.WorkbookExecState.PRODUCING.code) {
                return EnumsApi.PlanProducingStatus.OK;
            }
            workbook.setExecState(EnumsApi.WorkbookExecState.PRODUCING.code);
            workbookCache.save(workbook);
            return EnumsApi.PlanProducingStatus.OK;
        });
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
            changeValidStatus(workbookId, false);
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

    // TODO 2019.08.27 is it good to synchronize whole method?
    //  but it's working actually
    public synchronized LaunchpadCommParamsYaml.AssignedTask getTaskAndAssignToStation(long stationId, boolean isAcceptOnlySigned, Long workbookId) {

        final Station station = stationCache.findById(stationId);
        if (station == null) {
            log.error("#705.140 Station wasn't found for id: {}", stationId);
            return null;
        }
        StationStatus ss;
        try {
            ss = StationStatusUtils.to(station.status);
        } catch (Throwable e) {
            log.error("#705.150 Error parsing current status of station:\n{}", station.status);
            log.error("#705.151 Error ", e);
            return null;
        }
        if (ss.taskParamsVersion < TaskParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion()) {
            // this station is blacklisted. ignore it
            return null;
        }

        List<Long> anyTaskId = taskRepository.findAnyActiveForStationId(Consts.PAGE_REQUEST_1_REC, stationId);
        if (!anyTaskId.isEmpty()) {
            // this station already has active task
            log.info("#705.160 can't assign any new task to the station #{} because this station has an active task #{}", stationId, anyTaskId);
            return null;
        }

        List<Long> workbookIds;
        // find task in specific workbook ( i.e. workbookId==null)?
        if (workbookId==null) {
            workbookIds = workbookRepository.findByExecStateOrderByCreatedOnAsc(EnumsApi.WorkbookExecState.STARTED.code);
        }
        else {
            WorkbookImpl workbook = workbookCache.findById(workbookId);
            if (workbook==null) {
                log.warn("#705.170 Workbook wasn't found for id: {}", workbookId);
                return null;
            }
            if (workbook.getExecState()!= EnumsApi.WorkbookExecState.STARTED.code) {
                log.warn("#705.180 Workbook wasn't started. Current exec state: {}", EnumsApi.WorkbookExecState.toState(workbook.getExecState()));
                return null;
            }
            workbookIds = List.of(workbook.id);
        }

        for (Long wbId : workbookIds) {
            LaunchpadCommParamsYaml.AssignedTask result = findUnassignedTaskAndAssign(wbId, station, isAcceptOnlySigned);
            if (result!=null) {
                return result;
            }
        }
        return null;
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

    private LaunchpadCommParamsYaml.AssignedTask findUnassignedTaskAndAssign(Long workbookId, Station station, boolean isAcceptOnlySigned) {

        LongHolder longHolder = bannedSince.computeIfAbsent(station.getId(), o -> new LongHolder(0));
        if (longHolder.value!=0 && System.currentTimeMillis() - longHolder.value < TimeUnit.MINUTES.toMillis(30)) {
            return null;
        }
        WorkbookImpl workbook = workbookRepository.findByIdForUpdate(workbookId);
        if (workbook==null) {
            return null;
        }
        final List<WorkbookParamsYaml.TaskVertex> vertices = findAllForAssigning(workbook);
        final StationStatus stationStatus = StationStatusUtils.to(station.status);

        int page = 0;
        Task resultTask = null;
        List<Task> tasks;
        while ((tasks = getAllByStationIdIsNullAndWorkbookIdAndIdIn(workbookId, vertices, page++)).size()>0) {
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
                        log.warn("#705.213 Can't assign task #{} to station #{} because this station doesn't have defined interpreter for snippet's env {}",
                                station.getId(), task.getId(), taskParamYaml.taskYaml.snippet.env
                        );
                        longHolder.value = System.currentTimeMillis();
                        continue;
                    }
                }

                final List<EnumsApi.OS> supportedOS = getSupportedOS(taskParamYaml);
                if (stationStatus.os!=null && !supportedOS.isEmpty() && !supportedOS.contains(stationStatus.os)) {
                    log.info("#705.217 Can't assign task #{} to station #{}, " +
                                    "because this station doesn't support required OS version. station: {}, snippet: {}",
                            station.getId(), task.getId(), stationStatus.os, supportedOS
                    );
                    longHolder.value = System.currentTimeMillis();
                    continue;
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
            return null;
        }

        // normal way of operation
        longHolder.value = 0;

        LaunchpadCommParamsYaml.AssignedTask assignedTask = new LaunchpadCommParamsYaml.AssignedTask();
        assignedTask.setTaskId(resultTask.getId());
        assignedTask.setWorkbookId(workbookId);
        assignedTask.setParams(resultTask.getParams());

        resultTask.setAssignedOn(System.currentTimeMillis());
        resultTask.setStationId(station.getId());
        resultTask.setExecState(EnumsApi.TaskExecState.IN_PROGRESS.value);
        resultTask.setResultResourceScheduledOn(0);

        taskRepository.save((TaskImpl)resultTask);
        updateTaskExecStateByWorkbookId(workbookId, resultTask.getId(), EnumsApi.TaskExecState.IN_PROGRESS.value);

        return assignedTask;
    }

    private List<EnumsApi.OS> getSupportedOS(TaskParamsYaml taskParamYaml) {
        if (taskParamYaml.taskYaml.snippet!=null) {
            return SnippetCoreUtils.getSupportedOS(taskParamYaml.taskYaml.snippet.metas);
        }
        return List.of();
    }

    private List<Task> getAllByStationIdIsNullAndWorkbookIdAndIdIn(Long workbookId, List<WorkbookParamsYaml.TaskVertex> vertices, int page) {
        final List<Long> idsForSearch = getIdsForSearch(vertices, page, 20);
        if (idsForSearch.isEmpty()) {
            return List.of();
        }
        return taskRepository.findForAssigning(workbookId, idsForSearch);
    }

    public List<Long> storeAllConsoleResults(List<StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult> results) {
        final Consumer<Task> action = t -> {
            if (t!=null) {
                updateTaskExecStateByWorkbookId(t.getWorkbookId(), t.getId(), t.getExecState());
            }
        };

        List<Long> ids = new ArrayList<>();
        for (StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result : results) {
            ids.add(result.taskId);
            taskPersistencer.storeExecResult(result, action);
        }
        return ids;
    }

    // section 'workbook graph methods'

    // read-only operations with graph
    public List<WorkbookParamsYaml.TaskVertex> findAll(WorkbookImpl workbook) {
        return workbookSyncService.getWithSyncReadOnly(workbook, () -> workbookGraphService.findAll(workbook));
    }

    public List<WorkbookParamsYaml.TaskVertex> findLeafs(WorkbookImpl workbook) {
        return workbookSyncService.getWithSyncReadOnly(workbook, () -> workbookGraphService.findLeafs(workbook));
    }

    public Set<WorkbookParamsYaml.TaskVertex> findDescendants(WorkbookImpl workbook, Long taskId) {
        return workbookSyncService.getWithSyncReadOnly(workbook, () -> workbookGraphService.findDescendants(workbook, taskId));
    }

    public List<WorkbookParamsYaml.TaskVertex> findAllForAssigning(WorkbookImpl workbook) {
        return workbookSyncService.getWithSyncReadOnly(workbook, () -> workbookGraphService.findAllForAssigning(workbook));
    }

    public List<WorkbookParamsYaml.TaskVertex> findAllBroken(WorkbookImpl workbook) {
        return workbookSyncService.getWithSyncReadOnly(workbook, () -> workbookGraphService.findAllBroken(workbook));
    }

    // write operations with graph
    public OperationStatusRest updateTaskExecStateByWorkbookId(Long workbookId, Long taskId, int execState) {
        return workbookSyncService.getWithSync(workbookId, workbook -> {
            final WorkbookOperationStatusWithTaskList status = updateTaskExecStateWithoutSync(workbook, taskId, execState);
            return status.status;
        });
    }

    private WorkbookOperationStatusWithTaskList updateTaskExecStateWithoutSync(WorkbookImpl workbook, Long taskId, int execState) {
        changeTaskState(taskId, EnumsApi.TaskExecState.from(execState));
        final WorkbookOperationStatusWithTaskList status = workbookGraphService.updateTaskExecState(workbook, taskId, execState);
        updateTasksStateInDb(status);
        return status;
    }

    public WorkbookOperationStatusWithTaskList updateGraphWithSettingAllChildrenTasksAsBroken(Long workbookId, Long taskId) {
        return workbookSyncService.getWithSync(workbookId, (workbook) -> workbookGraphService.updateGraphWithSettingAllChildrenTasksAsBroken(workbook, taskId));
    }

    public OperationStatusRest addNewTasksToGraph(Long workbookId, List<Long> parentTaskIds, List<Long> taskIds) {
        final OperationStatusRest withSync = workbookSyncService.getWithSync(workbookId, (workbook) -> workbookGraphService.addNewTasksToGraph(workbook, parentTaskIds, taskIds));
        return withSync != null ? withSync : OperationStatusRest.OPERATION_STATUS_OK;
    }

    public WorkbookOperationStatusWithTaskList updateGraphWithResettingAllChildrenTasks(Long workbookId, Long taskId) {
        return workbookSyncService.getWithSync(workbookId, (workbook) -> workbookGraphService.updateGraphWithResettingAllChildrenTasks(workbook, taskId));
    }

    public WorkbookOperationStatusWithTaskList updateTaskExecStates(Long workbookId, ConcurrentHashMap<Long, Integer> taskStates) {
        if (taskStates==null || taskStates.isEmpty()) {
            return new WorkbookOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);
        }
        return workbookSyncService.getWithSync(workbookId, (workbook) -> {
            final WorkbookOperationStatusWithTaskList status = workbookGraphService.updateTaskExecStates(workbook, taskStates);
            updateTasksStateInDb(status);
            return status;
        });
    }

    // end of section 'workbook graph methods'


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
