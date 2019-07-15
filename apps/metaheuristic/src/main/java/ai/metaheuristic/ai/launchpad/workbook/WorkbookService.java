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
import ai.metaheuristic.ai.launchpad.atlas.AtlasService;
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.launchpad.beans.Station;
import ai.metaheuristic.ai.launchpad.beans.TaskImpl;
import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.binary_data.SimpleCodeAndStorageUrl;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentService;
import ai.metaheuristic.ai.launchpad.experiment.task.SimpleTaskExecResult;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.repositories.ExperimentRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

@Service
@Profile("launchpad")
@Slf4j
@RequiredArgsConstructor
public class WorkbookService implements ApplicationEventPublisherAware {

    private static final TasksAndAssignToStationResult EMPTY_RESULT = new TasksAndAssignToStationResult(null);

    private final Globals globals;
    private final WorkbookRepository workbookRepository;
    private final PlanCache planCache;
    private final BinaryDataService binaryDataService;
    private final TaskRepository taskRepository;
    private final ExperimentService experimentService;
    private final ExperimentRepository experimentRepository;
    private final AtlasService atlasService;
    private final TaskPersistencer taskPersistencer;
    private final StationCache stationCache;
    private final WorkbookCache workbookCache;
    private final WorkbookGraphService workbookGraphService;

    private ApplicationEventPublisher publisher;

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
            taskPersistencer.finishTaskAsBroken(taskId);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#705.020 Can't re-run task "+taskId+", this task is orphan and doesn't belong to any workbook");
        }

        Task t = taskPersistencer.resetTask(task);
        if (t==null) {
            WorkbookGraphService.OperationStatusWithTaskList withTaskList = workbookGraphService.updateGraphWithInvalidatingAllChildrenTasks(workbook, task.id);
            if (withTaskList.status.status== EnumsApi.OperationStatus.ERROR) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#705.030 Can't re-run task #" + taskId + ", see log for more information");
            }
            withTaskList.tasks.forEach( tt -> {
                taskPersistencer.resetTask(tt.taskId);
            });
        }
        else {
            WorkbookGraphService.OperationStatusWithTaskList withTaskList = workbookGraphService.updateGraphWithResettingAllChildrenTasks(workbook, task.id);
            if (withTaskList.status.status== EnumsApi.OperationStatus.ERROR) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#705.040 Can't re-run task #" + taskId + ", see log for more information");
            }
            withTaskList.tasks.forEach( tt -> {
                taskPersistencer.resetTask(tt.taskId);
            });
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

    public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void updateWorkbookStatuses(boolean needReconciliation) {
        List<WorkbookImpl> workbooks = workbookRepository.findByExecState(EnumsApi.WorkbookExecState.STARTED.code);
        for (WorkbookImpl workbook : workbooks) {
            updateWorkbookStatus(workbook, needReconciliation);
        }
    }

    public WorkbookImpl updateWorkbookStatus(WorkbookImpl workbook, boolean needReconciliation) {

        final long countUnfinishedTasks = workbookGraphService.getCountUnfinishedTasks(workbook);
        if (countUnfinishedTasks==0) {
            log.info("Workbook #{} was finished", workbook.getId());
            experimentService.updateMaxValueForExperimentFeatures(workbook.getId());
            WorkbookImpl instance = toFinished(workbook.getId());

            Long experimentId = experimentRepository.findIdByWorkbookId(instance.getId());
            if (experimentId==null) {
                log.info("#705.050 Can't store an experiment to atlas, the workbook "+instance.getId()+" doesn't contain an experiment" );
                return instance;
            }
            atlasService.toAtlas(instance.getId(), experimentId);
            return instance;
        }
        else {
            if (needReconciliation) {
                List<Object[]>  list = taskRepository.findAllExecStateByWorkbookId(workbook.getId());
                List<WorkbookParamsYaml.TaskVertex> vertices = workbookGraphService.findAll(workbook);
                Map<Long, Integer> states = new HashMap<>(list.size()+1);
                for (Object[] o : list) {
                    Long taskId = (Long) o[0];
                    Integer execState = (Integer) o[1];
                    states.put(taskId, execState);
                }
                final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
                vertices.stream().parallel().forEach(tv -> {
                        Integer state = states.get(tv.taskId);
                        if (state==null) {
                            readWriteLock.writeLock().lock();
                            try {
                                log.info("#705.052 Found non-created task, graph consistency is failed");
                                WorkbookImpl instance = toError(workbook.getId());
                            } finally {
                                readWriteLock.writeLock().unlock();
                            }
                        }
                        else if (tv.execState.value!=state) {
                            readWriteLock.writeLock().lock();
                            try {
                                log.info("#705.054 Found different states for task #"+tv.taskId+", " +
                                        "db: "+ EnumsApi.TaskExecState.from(state)+", " +
                                        "graph: "+tv.execState);
                                workbookGraphService.updateTaskExecState(workbook, tv.taskId, state);
                            } finally {
                                readWriteLock.writeLock().unlock();
                            }
                        }
                });
            }
        }
        return workbook;
    }

    public WorkbookImpl toProduced(Long workbookId) {
        return toState(workbookId, EnumsApi.WorkbookExecState.PRODUCED);
    }

    public WorkbookImpl toFinished(Long workbookId) {
        return toStateWithCompletion(workbookId, EnumsApi.WorkbookExecState.FINISHED);
    }

    public WorkbookImpl toError(Long workbookId) {
        return toStateWithCompletion(workbookId, EnumsApi.WorkbookExecState.ERROR);
    }

    public WorkbookImpl toStateWithCompletion(Long workbookId, EnumsApi.WorkbookExecState state) {
        WorkbookImpl workbook = workbookRepository.findByIdForUpdate(workbookId);
        if (workbook==null) {
            String es = "#705.080 Can't change exec state to "+state+" for workbook #" + workbookId;
            log.error(es);
            throw new IllegalStateException(es);
        }
        workbook.setCompletedOn(System.currentTimeMillis());
        workbook.setExecState(state.code);
        workbook = workbookCache.save(workbook);
        return workbook;
    }

    public WorkbookImpl toState(Long workbookId, EnumsApi.WorkbookExecState state) {
        WorkbookImpl workbook = workbookRepository.findByIdForUpdate(workbookId);
        if (workbook==null) {
            String es = "#705.082 Can't change exec state to "+state+" for workbook #" + workbookId;
            log.error(es);
            throw new IllegalStateException(es);
        }
        workbook.setExecState(state.code);
        workbook = workbookCache.save(workbook);
        return workbook;
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

    public void deleteById(long workbookId) {
        publisher.publishEvent( new WorkbookDeletionEvent(this, workbookId) );
        workbookCache.deleteById(workbookId);
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

        List<Workbook> workbooks;
        if (workbookId==null) {
            workbooks = workbookRepository.findByExecStateOrderByCreatedOnAsc(
                    EnumsApi.WorkbookExecState.STARTED.code);
        }
        else {
            Workbook workbook = workbookRepository.findById(workbookId).orElse(null);
            if (workbook==null) {
                log.warn("#705.170 Workbook wasn't found for id: {}", workbookId);
                return EMPTY_RESULT;
            }
            if (workbook.getExecState()!= EnumsApi.WorkbookExecState.STARTED.code) {
                log.warn("#705.180 Workbook wasn't started. Current exec state: {}", EnumsApi.WorkbookExecState.toState(workbook.getExecState()));
                return EMPTY_RESULT;
            }
            workbooks = Collections.singletonList(workbook);
        }

        for (Workbook workbook : workbooks) {
            TasksAndAssignToStationResult result = findUnassignedTaskAndAssign(workbook, station, isAcceptOnlySigned);
            if (!result.equals(EMPTY_RESULT)) {
                return result;
            }
        }
        return EMPTY_RESULT;
    }

    private final Map<Long, LongHolder> bannedSince = new HashMap<>();

    private TasksAndAssignToStationResult findUnassignedTaskAndAssign(Workbook workbook, Station station, boolean isAcceptOnlySigned) {

        LongHolder longHolder = bannedSince.computeIfAbsent(station.getId(), o -> new LongHolder(0));
        if (longHolder.value!=0 && System.currentTimeMillis() - longHolder.value < TimeUnit.MINUTES.toMillis(30)) {
            return EMPTY_RESULT;
        }

        int page = 0;
        Task resultTask = null;
        Slice<Task> tasks;
        while ((tasks=taskRepository.findForAssigning(PageRequest.of(page++, 20), workbook.getId())).hasContent()) {
            for (Task task : tasks) {
                final TaskParamsYaml taskParamYaml;
                try {
                    taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
                }
                catch (YAMLException e) {
                    log.error("#705.190 Task #{} has broken params yaml and will be skipped, error: {}, params:\n{}", task.getId(), e.toString(),task.getParams());
                    taskPersistencer.finishTaskAsBroken(task.getId());
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
        assignedTask.setWorkbookId(workbook.getId());
        assignedTask.setParams(resultTask.getParams());

        resultTask.setAssignedOn(System.currentTimeMillis());
        resultTask.setStationId(station.getId());
        resultTask.setExecState(EnumsApi.TaskExecState.IN_PROGRESS.value);
        resultTask.setResultResourceScheduledOn(0);

        taskRepository.saveAndFlush((TaskImpl)resultTask);

        return new TasksAndAssignToStationResult(assignedTask);
    }

    public List<Long> storeAllConsoleResults(List<SimpleTaskExecResult> results) {
        final TaskPersistencer.PostTaskCreationAction action = t -> {
            if (t!=null) {
                WorkbookImpl workbook = workbookRepository.findByIdForUpdate(t.getWorkbookId());
                workbookGraphService.updateTaskExecState(workbook, t.getId(), t.getExecState());
            }
        };

        List<Long> ids = new ArrayList<>();
        for (SimpleTaskExecResult result : results) {
            ids.add(result.taskId);
            taskPersistencer.storeExecResult(result, action);
        }
        return ids;
    }


}
