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
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.launchpad.repositories.PlanRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtils;
import ai.metaheuristic.ai.yaml.plan.PlanYamlUtils;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data.OperationStatusRest;
import ai.metaheuristic.api.v1.data.PlanApiData;
import ai.metaheuristic.api.v1.launchpad.Plan;
import ai.metaheuristic.api.v1.launchpad.Workbook;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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

    @SuppressWarnings("Duplicates")
    public PlanApiData.PlansResult getPlans(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.planRowsLimit, pageable);
        List<Plan> plans = planRepository.findAllByOrderByIdDesc();
        AtomicInteger count = new AtomicInteger();
        plans = plans.stream()
                .filter(o-> {
                    try {
                        PlanApiData.PlanParamsYaml ppy = PlanParamsYamlUtils.to(o.getParams());
                        final boolean b = ppy.internalParams == null || !ppy.internalParams.archived;
                        if (b) {
                            count.incrementAndGet();
                        }
                        return b;
                    } catch (YAMLException e) {
                        log.error("#560.300 Can't parse Plan params. It's broken or unknown version. Plan id: #{}", o.getId());
                        log.error("#560.301 Params:\n{}", o.getParams());
                        log.error("#560.302 Error: {}", e.toString());
                        return false;
                    }
                })
                .skip(pageable.getOffset())
                .peek(o-> o.setParams(null))
                .collect(Collectors.toList());

        PlanApiData.PlansResult plansResultRest = new PlanApiData.PlansResult();
        plansResultRest.items = new PageImpl<>(plans.subList(0, plans.size()<pageable.getPageSize()?plans.size():pageable.getPageSize()), pageable, count.get());

        return plansResultRest;
    }

    public PlanApiData.PlanResult getPlan(Long id) {
        final PlanImpl plan = planCache.findById(id);
        if (plan == null) {
            return new PlanApiData.PlanResult(
                    "#560.001 plan wasn't found, planId: " + id,
                    EnumsApi.PlanValidateStatus.PLAN_NOT_FOUND_ERROR );
        }
        String s = PlanYamlUtils.toString(
                PlanParamsYamlUtils.to(plan.getParams()).planYaml);

        return new PlanApiData.PlanResult(plan, s);
    }

    public PlanApiData.PlanResult validatePlan(Long id) {
        final PlanImpl plan = planCache.findById(id);
        if (plan == null) {
            return new PlanApiData.PlanResult("#560.002 plan wasn't found, planId: " + id,
                    EnumsApi.PlanValidateStatus.PLAN_NOT_FOUND_ERROR );
        }
        String s = PlanYamlUtils.toString(
                PlanParamsYamlUtils.to(plan.getParams()).planYaml);

        PlanApiData.PlanResult result = new PlanApiData.PlanResult(plan, s);
        PlanApiData.PlanValidation planValidation = planService.validateInternal(plan);
        result.errorMessages = planValidation.errorMessages;
        result.infoMessages = planValidation.infoMessages;
        result.status = planValidation.status;
        return result;
    }

    public PlanApiData.PlanResult addPlan(PlanImpl plan, String planYamlAsStr) {
        return processPlanCommit(plan, planYamlAsStr);
    }

    public PlanApiData.PlanResult updatePlan(Plan planModel, String planYamlAsStr) {
        PlanImpl plan = planCache.findById(planModel.getId());
        if (plan == null) {
            return new PlanApiData.PlanResult(
                    "#560.010 plan wasn't found, planId: " + planModel.getId(),
                    EnumsApi.PlanValidateStatus.PLAN_NOT_FOUND_ERROR );
        }
        plan.setCode(planModel.getCode());

        return processPlanCommit(plan, planYamlAsStr);
    }

    private PlanApiData.PlanResult processPlanCommit(PlanImpl plan, String planYamlAsStr) {
        if (StringUtils.isBlank(planYamlAsStr)) {
            return new PlanApiData.PlanResult("#560.017 plan yaml is empty");
        }
        if (StringUtils.isBlank(plan.code)) {
            return new PlanApiData.PlanResult("#560.020 code of plan is empty");
        }
        if (StringUtils.isBlank(plan.code)) {
            return new PlanApiData.PlanResult("#560.030 plan is empty");
        }
        Plan f = planRepository.findByCode(plan.code);
        if (f!=null && !f.getId().equals(plan.getId())) {
            return new PlanApiData.PlanResult("#560.033 plan with such code already exists, code: " + plan.code);
        }

        // we don't use full PlanParamYaml, only PlanYamlUtils field
        PlanApiData.PlanParamsYaml planParamsYaml = setNewPlanYaml(plan, planYamlAsStr);

        plan = planCache.save(plan);

        PlanApiData.PlanResult result = new PlanApiData.PlanResult(plan, PlanYamlUtils.toString(planParamsYaml.planYaml) );
        PlanApiData.PlanValidation planValidation = planService.validateInternal(result.plan);
        result.infoMessages = planValidation.infoMessages;
        result.errorMessages = planValidation.errorMessages;
        return result;
    }

    private PlanApiData.PlanParamsYaml setNewPlanYaml(PlanImpl plan, String planYamlAsStr) {
        PlanApiData.PlanParamsYaml ppy = plan.params!=null ? PlanParamsYamlUtils.to(plan.params) : new PlanApiData.PlanParamsYaml();
        ppy.planYaml = PlanYamlUtils.toPlanYaml(planYamlAsStr);
        plan.setParams(PlanParamsYamlUtils.toString(ppy));
        return ppy;
    }

    public OperationStatusRest deletePlanById(Long id) {
        Plan plan = planCache.findById(id);
        if (plan == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.050 plan wasn't found, planId: " + id);
        }
        planCache.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest archivePlanById(Long id) {
        PlanImpl plan = planCache.findById(id);
        if (plan == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.055 plan wasn't found, planId: " + id);
        }
        PlanApiData.PlanParamsYaml ppy = PlanParamsYamlUtils.to(plan.params);
        if (ppy.internalParams==null) {
            ppy.internalParams = new PlanApiData.PlanInternalParamsYaml();
        }
        ppy.internalParams.archived = true;
        plan.params = PlanParamsYamlUtils.toString(ppy);

        plan = planCache.save(plan);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    // ============= Workbooks =============

    public PlanApiData.WorkbooksResult getWorkbooksOrderByCreatedOnDesc(Long id, Pageable pageable) {
        return planService.getWorkbooksOrderByCreatedOnDescResult(id, pageable);
    }

    public PlanApiData.WorkbookResult addWorkbook(Long planId, String poolCode, String inputResourceParams) {
        PlanApiData.WorkbookResult result = new PlanApiData.WorkbookResult(planCache.findById(planId));
        if (result.plan == null) {
            result.addErrorMessage("#560.060 plan wasn't found, planId: " + planId);
            return result;
        }

        if (StringUtils.isBlank(poolCode) && StringUtils.isBlank(inputResourceParams) ) {
            result.addErrorMessage("#560.063 both inputResourcePoolCode of Workbook and inputResourceParams are empty");
            return result;
        }

        if (StringUtils.isNotBlank(poolCode) && StringUtils.isNotBlank(inputResourceParams) ) {
            result.addErrorMessage("#560.065 both inputResourcePoolCode of Workbook and inputResourceParams aren't empty");
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
            result.addErrorMessage("#560.072 Error creating workbook: " + producingResult.planProducingStatus);
            return result;
        }
        result.workbook = producingResult.workbook;

        // ugly work-around on ObjectOptimisticLockingFailureException, StaleObjectStateException
        result.plan = planCache.findById(planId);
        if (result.plan == null) {
            return new PlanApiData.WorkbookResult("#560.073 plan wasn't found, planId: " + planId);
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
            result.addErrorMessage("#560.077 plan producing was failed, status: " + countTasks.planProducingStatus);
            return result;
        }

        if (globals.maxTasksPerPlan < countTasks.numberOfTasks) {
            planService.changeValidStatus(producingResult.workbook, false);
            result.addErrorMessage("#560.081 number of tasks for this workbook exceeded the allowed maximum number. Workbook was created but its status is 'not valid'. " +
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

    public OperationStatusRest deleteWorkbookById(Long workbookId) {
        PlanApiData.WorkbookResult result = planService.getWorkbookExtended(workbookId);
        if (CollectionUtils.isNotEmpty(result.errorMessages)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, result.errorMessages);
        }

        Workbook fi = workbookRepository.findById(workbookId).orElse(null);
        if (fi==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.084 Workbook wasn't found, workbookId: " + workbookId );
        }
        planService.deleteWorkbook(workbookId, fi.getPlanId());
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest changeWorkbookExecState(String state, Long workbookId) {
        EnumsApi.WorkbookExecState execState = EnumsApi.WorkbookExecState.valueOf(state.toUpperCase());
        if (execState== EnumsApi.WorkbookExecState.UNKNOWN) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.070 Unknown exec state, state: " + state);
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
                    List.of("#560.100 plan wasn't found, workbookId: " + workbookId),
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
                    "#560.057 workbook wasn't found, workbookId: " + workbookId);
        }
        planService.changeValidStatus(workbook, state);
        return OperationStatusRest.OPERATION_STATUS_OK;

    }
}
