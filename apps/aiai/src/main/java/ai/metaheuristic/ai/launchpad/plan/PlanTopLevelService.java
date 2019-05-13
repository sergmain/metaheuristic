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

package ai.metaheuristic.ai.launchpad.plan;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.api.v1.launchpad.Plan;
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.api.v1.launchpad.Workbook;
import ai.metaheuristic.api.v1.data.PlanApiData;
import ai.metaheuristic.api.v1.data.OperationStatusRest;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.repositories.PlanRepository;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.v1.EnumsApi;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Profile("launchpad")
@Service
public class PlanTopLevelService {

    private final Globals globals;
    private final PlanCache planCache;
    private final PlanService planService;
    private final PlanRepository planRepository;
    private final WorkbookRepository workbookRepository;

    public PlanTopLevelService(Globals globals, PlanCache planCache, PlanService planService, PlanRepository planRepository1, WorkbookRepository workbookRepository) {
        this.globals = globals;
        this.planCache = planCache;
        this.planService = planService;
        this.planRepository = planRepository1;
        this.workbookRepository = workbookRepository;
    }

    public PlanApiData.PlansResult getPlans(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.planRowsLimit, pageable);
        PlanApiData.PlansResult result = new PlanApiData.PlansResult();
        result.items = planRepository.findAllByOrderByIdDesc(pageable);
        result.items.forEach( o -> o.setParams(null) );
        return result;
    }

    public PlanApiData.PlanResult getPlan(Long id) {
        final PlanImpl plan = planCache.findById(id);
        if (plan == null) {
            return new PlanApiData.PlanResult(
                    "#560.01 plan wasn't found, planId: " + id,
                    EnumsApi.PlanValidateStatus.PLAN_NOT_FOUND_ERROR );
        }
        return new PlanApiData.PlanResult(plan);
    }

    public PlanApiData.PlanResult validatePlan(Long id) {
        final PlanImpl plan = planCache.findById(id);
        if (plan == null) {
            return new PlanApiData.PlanResult("#560.02 plan wasn't found, planId: " + id,
                    EnumsApi.PlanValidateStatus.PLAN_NOT_FOUND_ERROR );
        }
        PlanApiData.PlanResult result = new PlanApiData.PlanResult(plan);
        PlanApiData.PlanValidation planValidation = planService.validateInternal(plan);
        result.errorMessages = planValidation.errorMessages;
        result.infoMessages = planValidation.infoMessages;
        result.status = planValidation.status;
        return result;
    }

    public PlanApiData.PlanResult addPlan(PlanImpl plan) {
        return processPlanCommit(plan);
    }

    public PlanApiData.PlanResult updatePlan(Plan planModel) {
        PlanImpl plan = planCache.findById(planModel.getId());
        if (plan == null) {
            return new PlanApiData.PlanResult(
                    "#560.10 plan wasn't found, planId: " + planModel.getId(),
                    EnumsApi.PlanValidateStatus.PLAN_NOT_FOUND_ERROR );
        }
        plan.setCode(planModel.getCode());
        plan.setParams(planModel.getParams());
        return processPlanCommit(plan);
    }

    private PlanApiData.PlanResult processPlanCommit(PlanImpl plan) {
        if (StringUtils.isBlank(plan.code)) {
            return new PlanApiData.PlanResult("#560.20 code of plan is empty");
        }
        if (StringUtils.isBlank(plan.code)) {
            return new PlanApiData.PlanResult("#560.30 plan is empty");
        }
        Plan f = planRepository.findByCode(plan.code);
        if (f!=null && !f.getId().equals(plan.getId())) {
            return new PlanApiData.PlanResult("#560.33 plan with such code already exists, code: " + plan.code);
        }
        PlanApiData.PlanResult result = new PlanApiData.PlanResult(planCache.save(plan));
        PlanApiData.PlanValidation planValidation = planService.validateInternal(result.plan);
        result.infoMessages = planValidation.infoMessages;
        result.errorMessages = planValidation.errorMessages;
        return result;
    }

    public OperationStatusRest deletePlanById(Long id) {
        Plan plan = planCache.findById(id);
        if (plan == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.50 plan wasn't found, planId: " + id);
        }
        planCache.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    // ============= Workbooks =============

    public PlanApiData.WorkbooksResult getWorkbooksOrderByCreatedOnDesc(Long id, Pageable pageable) {
        return planService.getWorkbooksOrderByCreatedOnDescResult(id, pageable);
    }

    public PlanApiData.WorkbookResult addWorkbook(Long planId, String poolCode, String inputResourceParams) {
        PlanApiData.WorkbookResult result = new PlanApiData.WorkbookResult(planCache.findById(planId));
        if (result.plan == null) {
            result.addErrorMessage("#560.60 plan wasn't found, planId: " + planId);
            return result;
        }

        if (StringUtils.isBlank(poolCode) && StringUtils.isBlank(inputResourceParams) ) {
            result.addErrorMessage("#560.63 both inputResourcePoolCode of Workbook and inputResourceParams are empty");
            return result;
        }

        if (StringUtils.isNotBlank(poolCode) && StringUtils.isNotBlank(inputResourceParams) ) {
            result.addErrorMessage("#560.65 both inputResourcePoolCode of Workbook and inputResourceParams aren't empty");
            return result;
        }

        // validate the plan
        PlanApiData.PlanValidation planValidation = planService.validateInternal(result.plan);
        if (planValidation.status != EnumsApi.PlanValidateStatus.OK ) {
            result.errorMessages = planValidation.errorMessages;
            return result;
        }

        PlanApiData.TaskProducingResultComplex producingResult = planService.createWorkbook(result.plan.getId(),
                StringUtils.isNotBlank(inputResourceParams) ? inputResourceParams : PlanService.asInputResourceParams(poolCode));
        if (producingResult.planProducingStatus!= EnumsApi.PlanProducingStatus.OK) {
            result.addErrorMessage("#560.72 Error creating workbook: " + producingResult.planProducingStatus);
            return result;
        }
        result.workbook = producingResult.workbook;

        // ugly work-around on StaleObjectStateException
        result.plan = planCache.findById(planId);
        if (result.plan == null) {
            return new PlanApiData.WorkbookResult("#560.73 plan wasn't found, planId: " + planId);
        }

        // validate the plan + the workbook
        planValidation = planService.validateInternal(result.plan);
        if (planValidation.status != EnumsApi.PlanValidateStatus.OK ) {
            result.errorMessages = planValidation.errorMessages;
            return result;
        }
        result.plan = planCache.findById(planId);

        PlanApiData.TaskProducingResultComplex countTasks = planService.produceTasks(false, result.getPlan(), producingResult.workbook);
        if (countTasks.planProducingStatus != EnumsApi.PlanProducingStatus.OK) {
            planService.changeValidStatus(producingResult.workbook, false);
            result.addErrorMessage("#560.77 plan producing was failed, status: " + countTasks.planProducingStatus);
            return result;
        }

        if (globals.maxTasksPerPlan < countTasks.numberOfTasks) {
            planService.changeValidStatus(producingResult.workbook, false);
            result.addErrorMessage("#560.81 number of tasks for this workbook exceeded the allowed maximum number. Workbook was created but its status is 'not valid'. " +
                    "Allowed maximum number of tasks: " + globals.maxTasksPerPlan+", tasks in this workbook:  " + countTasks.numberOfTasks);
            return result;
        }
        planService.changeValidStatus(producingResult.workbook, true);

        return result;
    }

    public PlanApiData.TaskProducingResult createWorkbook(Long planId, String inputResourceParam) {
        final PlanApiData.TaskProducingResultComplex result = planService.createWorkbook(planId, inputResourceParam);
        return new PlanApiData.TaskProducingResult(
                result.getStatus()== EnumsApi.TaskProducingStatus.OK
                        ? new ArrayList<>()
                        : List.of("Error of creating workbook, " +
                        "validation status: " + result.getPlanValidateStatus()+", producing status: " + result.getPlanProducingStatus()),
                result.planValidateStatus,
                result.planProducingStatus,
                result.workbook.getId()
        );
    }

    public PlanApiData.WorkbookResult getWorkbookExtended(Long workbookId) {
        //noinspection UnnecessaryLocalVariable
        PlanApiData.WorkbookResult result = planService.getWorkbookExtended(workbookId);
        return result;
    }

    public OperationStatusRest deleteWorkbookById(Long planId, Long workbookId) {
        PlanApiData.WorkbookResult result = planService.getWorkbookExtended(workbookId);
        if (CollectionUtils.isNotEmpty(result.errorMessages)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, result.errorMessages);
        }

        Workbook fi = workbookRepository.findById(workbookId).orElse(null);
        if (fi==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.84 Workbook wasn't found, workbookId: " + workbookId );
        }
        planService.deleteWorkbook(workbookId, fi.getPlanId());
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest changeWorkbookExecState(String state, Long workbookId) {
        EnumsApi.WorkbookExecState execState = EnumsApi.WorkbookExecState.valueOf(state.toUpperCase());
        if (execState== EnumsApi.WorkbookExecState.UNKNOWN) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.87 Unknown exec state, state: " + state);
        }
        //noinspection UnnecessaryLocalVariable
        OperationStatusRest status = planService.workbookTargetExecState(workbookId, execState);
        return status;
    }

    // ============= Service methods =============

    public PlanApiData.TaskProducingResult produceTasksWithoutPersistence(long workbookId) {

        PlanApiData.WorkbookResult workbook = planService.getWorkbookExtended(workbookId);
        if (workbook.isErrorMessages()) {
            return new PlanApiData.TaskProducingResult(
                    List.of("#560.10 plan wasn't found, workbookId: " + workbookId),
                    EnumsApi.PlanValidateStatus.PLAN_NOT_FOUND_ERROR, null, null );

        }

        final PlanApiData.TaskProducingResultComplex result = planService.produceTasks(false, workbook.getPlan(), workbook.workbook);
        return new PlanApiData.TaskProducingResult(List.of("See statuses to determine what is actual status"), result.planValidateStatus, result.planProducingStatus,
                result.workbook!=null ? result.workbook.getId() : null);
    }

    public void createAllTasks() {
        planService.createAllTasks();
    }

    public OperationStatusRest changeValidStatus(Long workbookId, boolean state) {
        Workbook workbook = workbookRepository.findById(workbookId).orElse(null);
        if (workbook == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.57 workbook wasn't found, workbookId: " + workbookId);
        }
        planService.changeValidStatus(workbook, state);
        return OperationStatusRest.OPERATION_STATUS_OK;

    }
}
