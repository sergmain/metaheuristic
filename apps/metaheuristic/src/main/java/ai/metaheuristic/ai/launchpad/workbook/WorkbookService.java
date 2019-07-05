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
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.launchpad.atlas.AtlasService;
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
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
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.input_resource_param.InputResourceParamUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.InputResourceParam;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.plan.PlanApiData;
import ai.metaheuristic.api.launchpad.Plan;
import ai.metaheuristic.api.launchpad.Task;
import ai.metaheuristic.api.launchpad.Workbook;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.function.Consumer;

@Service
@Profile("launchpad")
@Slf4j
@RequiredArgsConstructor
public class WorkbookService implements ApplicationEventPublisherAware {

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

    private ApplicationEventPublisher publisher;

    private void updateGraphWithResettingAllChildrenTasks(WorkbookImpl workbook) {

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
        updateGraphWithResettingAllChildrenTasks(workbook);

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

        InputResourceParam resourceParam = InputResourceParamUtils.to(inputResourceParam);
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
        fi.setProducingOrder(Consts.TASK_ORDER_START_VALUE);
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


}
