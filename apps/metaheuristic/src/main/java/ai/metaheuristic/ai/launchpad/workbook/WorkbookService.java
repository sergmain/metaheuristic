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
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.plan.PlanService;
import ai.metaheuristic.ai.launchpad.repositories.ExperimentRepository;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.station.StationCache;
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.utils.holders.LongHolder;
import ai.metaheuristic.ai.yaml.workbook.WorkbookParamsYamlUtils;
import ai.metaheuristic.ai.yaml.station_status.StationStatus;
import ai.metaheuristic.ai.yaml.station_status.StationStatusUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.plan.PlanApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.launchpad.Plan;
import ai.metaheuristic.api.launchpad.Task;
import ai.metaheuristic.api.launchpad.Workbook;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
    private final WorkbookService workbookService;
    private final ExperimentService experimentService;
    private final BinaryDataService binaryDataService;
    private final PlanService planService;
    private final TaskRepository taskRepository;
    private final ExperimentRepository experimentRepository;
    private final AtlasService atlasService;
    private final TaskPersistencer taskPersistencer;
    private final StationCache stationCache;

    private ApplicationEventPublisher publisher;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TasksAndAssignToStationResult {
        Protocol.AssignedTask.Task simpleTask;
    }


    private void updateGraphWithResettingAllChildrenTasks(WorkbookImpl workbook, Long taskId) {

    }

    public void updateGraphWithInvalidatingAllChildrenTasks(WorkbookImpl workbook, Long taskId) {

    }

    public void addNewTasksToGraph(Workbook wb, List<Long> parentTaskIds, List<Long> taskIds) {

    }

    public OperationStatusRest resetTask(long taskId) {
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.280 Can't re-run task "+taskId+", task with such taskId wasn't found");
        }
        WorkbookImpl workbook = workbookRepository.findById(task.getWorkbookId()).orElse(null);
        if (workbook == null) {
            taskPersistencer.finishTaskAsBroken(taskId);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.290 Can't re-run task "+taskId+", this task is orphan and doesn't belong to any workbook");
        }

        Task t = taskPersistencer.resetTask(task);
        if (t==null) {
            updateGraphWithInvalidatingAllChildrenTasks(workbook, task.id);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.300 Can't re-run task #"+taskId+", see log for more information");
        }
        updateGraphWithResettingAllChildrenTasks(workbook, task.id);

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

    public void markOrderAsProcessed() {
        List<WorkbookImpl> workbooks = workbookRepository.findByExecState(EnumsApi.WorkbookExecState.STARTED.code);
        for (Workbook workbook : workbooks) {
            markOrderAsProcessed(workbook);
        }
    }

    public Workbook markOrderAsProcessed(Workbook workbook) {

        if (getCountUnfinishedTasks(workbook.getId())==0) {
            log.info("Workbook #{} was finished", workbook.getId());
            experimentService.updateMaxValueForExperimentFeatures(workbook.getId());
            workbook.setCompletedOn(System.currentTimeMillis());
            workbook.setExecState(EnumsApi.WorkbookExecState.FINISHED.code);
            Workbook instance = save(workbook);

            Long experimentId = experimentRepository.findIdByWorkbookId(instance.getId());
            if (experimentId==null) {
                log.info("#701.230 Can't store an experiment to atlas, the workbook "+instance.getId()+" doesn't contain an experiment" );
                return instance;
            }
            atlasService.toAtlas(instance.getId(), experimentId);
            return instance;
        }
        return workbook;
/*

        List<Long> anyTask = taskRepository.findAnyNotAssignedWithConcreteOrder(Consts.PAGE_REQUEST_1_REC, workbook.getId(), workbook.getProducingOrder() );
        if (!anyTask.isEmpty()) {
            return workbook;
        }
        List<Task> forChecking = taskRepository.findWithConcreteOrder(workbook.getId(), workbook.getProducingOrder() );
        if (forChecking.isEmpty()) {
            Long count = taskRepository.countWithConcreteOrder(workbook.getId(), workbook.getProducingOrder() + 1);
            if (count==null) {
                throw new IllegalStateException("#701.220 count of records is null");
            }
            if (count==0) {
                log.info("Workbook #{} was finished", workbook.getId());
                experimentService.updateMaxValueForExperimentFeatures(workbook.getId());
                workbook.setCompletedOn(System.currentTimeMillis());
                workbook.setExecState(EnumsApi.WorkbookExecState.FINISHED.code);
                Workbook instance = save(workbook);

                Long experimentId = experimentRepository.findIdByWorkbookId(instance.getId());
                if (experimentId==null) {
                    log.info("#701.230 Can't store an experiment to atlas, the workbook "+instance.getId()+" doesn't contain an experiment" );
                    return instance;
                }
                atlasService.toAtlas(instance.getId(), experimentId);
                return instance;
            }
            return workbook;
        }
        for (Task task : forChecking) {
            if (!task.isCompleted()) {
                return workbook;
            }
        }
        workbook.setProducingOrder(workbook.getProducingOrder()+1);
        return save(workbook);
*/
    }

    private int getCountUnfinishedTasks(Long workbookId) {
        if (true) throw new NotImplementedException("Not yet");
        return 0;
    }

    public OperationStatusRest workbookTargetExecState(Long workbookId, EnumsApi.WorkbookExecState execState) {
        PlanApiData.WorkbookResult result = getWorkbookExtended(workbookId);
        if (result.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, result.errorMessages);
        }

        final Workbook workbook = result.workbook;
        final Plan plan = result.plan;
        if (plan ==null || workbook ==null) {
            throw new IllegalStateException("#701.110 Error: (result.plan==null || result.workbook==null)");
        }

        workbook.setExecState(execState.code);
        save(workbook);

        planService.setLockedTo(plan, true);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public void toProduced(boolean isPersist, PlanApiData.TaskProducingResultComplex result, Workbook fi) {
        if (!isPersist) {
            return;
        }
        Long id = fi.getId();
        result.workbook = workbookRepository.findById(id).orElse(null);
        if (result.workbook==null) {
            String es = "#701.210 Can't change exec state to PRODUCED for workbook #" + id;
            log.error(es);
            throw new IllegalStateException(es);
        }
        result.workbook.setExecState(EnumsApi.WorkbookExecState.PRODUCED.code);
        save(result.workbook);
    }

    public PlanApiData.TaskProducingResultComplex createWorkbook(Long planId, String inputResourceParam) {
        PlanApiData.TaskProducingResultComplex result = new PlanApiData.TaskProducingResultComplex();

        WorkbookParamsYaml resourceParam = WorkbookParamsYamlUtils.BASE_YAML_UTILS.to(inputResourceParam);
        List<SimpleCodeAndStorageUrl> inputResourceCodes = binaryDataService.getResourceCodesInPool(resourceParam.getAllPoolCodes());
        if (inputResourceCodes==null || inputResourceCodes.isEmpty()) {
            result.planProducingStatus = EnumsApi.PlanProducingStatus.INPUT_POOL_CODE_DOESNT_EXIST_ERROR;
            return result;
        }

        Workbook fi = new WorkbookImpl();
        fi.setPlanId(planId);
        fi.setCreatedOn(System.currentTimeMillis());
        fi.setExecState(EnumsApi.WorkbookExecState.NONE.code);
        fi.setCompletedOn(null);
        fi.setInputResourceParam(inputResourceParam);
        fi.setValid(true);

        workbookService.save(fi);
        result.planProducingStatus = EnumsApi.PlanProducingStatus.OK;
        result.workbook = fi;

        return result;
    }

    public void toStopped(boolean isPersist, long workbookId) {
        if (!isPersist) {
            return;
        }
        Workbook fi = workbookRepository.findById(workbookId).orElse(null);
        if (fi==null) {
            return;
        }
        fi.setExecState(EnumsApi.WorkbookExecState.STOPPED.code);
        save(fi);
    }

    public void changeValidStatus(Workbook workbook, boolean status) {
        workbook.setValid(status);
        save(workbook);
    }

    public EnumsApi.PlanProducingStatus toProducing(Workbook fi) {
        fi.setExecState(EnumsApi.WorkbookExecState.PRODUCING.code);
        save(fi);
        return EnumsApi.PlanProducingStatus.OK;
    }

    public Workbook save(Workbook workbook) {
        if (workbook instanceof WorkbookImpl) {
            return workbookRepository.saveAndFlush((WorkbookImpl)workbook);
        }
        else {
            throw new NotImplementedException("#701.130 Need to implement");
        }
    }

    public void deleteWorkbook(Long workbookId, long planId) {
        experimentService.resetExperiment(workbookId);
        workbookService.deleteById(workbookId);
        binaryDataService.deleteByRefId(workbookId, EnumsApi.BinaryDataRefType.workbook);
        Workbook workbook = workbookRepository.findFirstByPlanId(planId);
        if (workbook==null) {
            Plan p = planCache.findById(planId);
            if (p!=null) {
                planService.setLockedTo(p, false);
            }
        }
    }

    public PlanApiData.WorkbookResult getWorkbookExtended(Long workbookId) {
        if (workbookId==null) {
            return new PlanApiData.WorkbookResult("#701.050 workbookId is null");
        }
        final WorkbookImpl workbook = workbookRepository.findById(workbookId).orElse(null);
        if (workbook == null) {
            return new PlanApiData.WorkbookResult("#701.060 workbook wasn't found, workbookId: " + workbookId);
        }
        PlanImpl plan = planCache.findById(workbook.getPlanId());
        if (plan == null) {
            return new PlanApiData.WorkbookResult("#701.070 plan wasn't found, planId: " + workbook.getPlanId());
        }

        if (!plan.getId().equals(workbook.getPlanId())) {
            workbook.setValid(false);
            workbookRepository.saveAndFlush(workbook);
            return new PlanApiData.WorkbookResult("#701.080 planId doesn't match to workbook.planId, planId: " + workbook.getPlanId()+", workbook.planId: " + workbook.getPlanId());
        }

        //noinspection UnnecessaryLocalVariable
        PlanApiData.WorkbookResult result = new PlanApiData.WorkbookResult(plan, workbook);
        return result;
    }

    public void deleteById(long workbookId) {
        publisher.publishEvent( new WorkbookDeletionEvent(this, workbookId) );
        workbookRepository.deleteById(workbookId);
    }

    public PlanApiData.WorkbooksResult getWorkbooksOrderByCreatedOnDescResult(@PathVariable Long id, @PageableDefault(size = 5) Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.workbookRowsLimit, pageable);
        PlanApiData.WorkbooksResult result = new PlanApiData.WorkbooksResult();
        result.instances = workbookRepository.findByPlanIdOrderByCreatedOnDesc(pageable, id);
        result.currentPlanId = id;

        for (Workbook workbook : result.instances) {
            Plan plan = planCache.findById(workbook.getPlanId());
            if (plan==null) {
                log.warn("#701.140 Found workbook with wrong planId. planId: {}", workbook.getPlanId());
                continue;
            }
            result.plans.put(workbook.getId(), plan);
        }
        return result;
    }

    public synchronized TasksAndAssignToStationResult getTaskAndAssignToStation(long stationId, boolean isAcceptOnlySigned, Long workbookId) {

        final Station station = stationCache.findById(stationId);
        if (station == null) {
            log.error("#317.47 Station wasn't found for id: {}", stationId);
            return EMPTY_RESULT;
        }
        StationStatus ss;
        try {
            ss = StationStatusUtils.to(station.status);
        } catch (Throwable e) {
            log.error("#317.32 Error parsing current status of station:\n{}", station.status);
            log.error("#317.33 Error ", e);
            return EMPTY_RESULT;
        }
        if (ss.taskParamsVersion < TaskParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion()) {
            // this station is blacklisted. ignore it
            return EMPTY_RESULT;
        }

        List<Long> anyTaskId = taskRepository.findAnyActiveForStationId(Consts.PAGE_REQUEST_1_REC, stationId);
        if (!anyTaskId.isEmpty()) {
            // this station already has active task
            log.info("#317.34 can't assign any new task to station #{} because this station has active task #{}", stationId, anyTaskId);
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
                log.warn("#317.39 Workbook wasn't found for id: {}", workbookId);
                return EMPTY_RESULT;
            }
            if (workbook.getExecState()!= EnumsApi.WorkbookExecState.STARTED.code) {
                log.warn("#317.42 Workbook wasn't started. Current exec state: {}", EnumsApi.WorkbookExecState.toState(workbook.getExecState()));
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
                    log.error("Task #{} has broken params yaml and will be skipped, error: {}, params:\n{}", task.getId(), e.toString(),task.getParams());
                    taskPersistencer.finishTaskAsBroken(task.getId());
                    continue;
                }
                catch (Exception e) {
                    throw new RuntimeException("#317.59 Error", e);
                }

                StationStatus stationStatus = StationStatusUtils.to(station.status);
                if (taskParamYaml.taskYaml.snippet.sourcing== EnumsApi.SnippetSourcing.git &&
                        stationStatus.gitStatusInfo.status!= Enums.GitStatus.installed) {
                    log.warn("#317.62 Can't assign task #{} to station #{} because this station doesn't correctly installed git, git status info: {}",
                            station.getId(), task.getId(), stationStatus.gitStatusInfo
                    );
                    longHolder.value = System.currentTimeMillis();
                    continue;
                }

                if (taskParamYaml.taskYaml.snippet.env!=null) {
                    String interpreter = stationStatus.env.getEnvs().get(taskParamYaml.taskYaml.snippet.env);
                    if (interpreter == null) {
                        log.warn("#317.64 Can't assign task #{} to station #{} because this station doesn't have defined interpreter for snippet's env {}",
                                station.getId(), task.getId(), taskParamYaml.taskYaml.snippet.env
                        );
                        longHolder.value = System.currentTimeMillis();
                        continue;
                    }
                }

                if (isAcceptOnlySigned) {
                    if (!taskParamYaml.taskYaml.snippet.info.isSigned()) {
                        log.warn("#317.69 Snippet with code {} wasn't signed", taskParamYaml.taskYaml.snippet.getCode());
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


}
