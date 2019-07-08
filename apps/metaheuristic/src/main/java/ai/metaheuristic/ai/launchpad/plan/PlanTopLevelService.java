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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.launchpad.repositories.PlanRepository;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookCache;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.plan.PlanApiData;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.api.launchpad.Plan;
import ai.metaheuristic.api.launchpad.Workbook;
import ai.metaheuristic.commons.exceptions.WrongVersionOfYamlFileException;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.Consts.YAML_EXT;
import static ai.metaheuristic.ai.Consts.YML_EXT;

@Slf4j
@Profile("launchpad")
@Service
@RequiredArgsConstructor
public class PlanTopLevelService {

    private final Globals globals;
    private final PlanCache planCache;
    private final PlanService planService;
    private final PlanRepository planRepository;
    private final WorkbookService workbookService;
    private final WorkbookCache workbookCache;

    public PlanApiData.WorkbookResult addWorkbook(Long planId, String poolCode, String inputResourceParams) {
        final PlanImpl plan = planCache.findById(planId);

        if (plan == null) {
            return new PlanApiData.WorkbookResult("#560.060 plan wasn't found, planId: " + planId);
        }
        if (StringUtils.isBlank(poolCode) && StringUtils.isBlank(inputResourceParams)) {
            return new PlanApiData.WorkbookResult("#560.063 both inputResourcePoolCode of Workbook and inputResourceParams are empty");
        }

        if (StringUtils.isNotBlank(poolCode) && StringUtils.isNotBlank(inputResourceParams)) {
            return new PlanApiData.WorkbookResult("#560.065 both inputResourcePoolCode of Workbook and inputResourceParams aren't empty");
        }

        // validate the plan
        PlanApiData.PlanValidation planValidation = planService.validateInternal(plan);
        if (planValidation.status != EnumsApi.PlanValidateStatus.OK) {
            PlanApiData.WorkbookResult r = new PlanApiData.WorkbookResult();
            r.errorMessages = planValidation.errorMessages;
            return r;
        }

        WorkbookParamsYaml wpy = StringUtils.isNotBlank(inputResourceParams)
                ? PlanUtils.parseToWorkbookParamsYaml(inputResourceParams)
                : asWorkbookParamsYaml(poolCode);
        PlanApiData.TaskProducingResultComplex producingResult = workbookService.createWorkbook(plan.getId(), wpy);
        if (producingResult.planProducingStatus != EnumsApi.PlanProducingStatus.OK) {
            return new PlanApiData.WorkbookResult("#560.072 Error creating workbook: " + producingResult.planProducingStatus);
        }

        PlanApiData.TaskProducingResultComplex countTasks = planService.produceTasks(false, plan, producingResult.workbook.getId());
        if (countTasks.planProducingStatus != EnumsApi.PlanProducingStatus.OK) {
            workbookService.changeValidStatus(producingResult.workbook.getId(), false);
            return new PlanApiData.WorkbookResult("#560.077 plan producing was failed, status: " + countTasks.planProducingStatus);
        }

        if (globals.maxTasksPerPlan < countTasks.numberOfTasks) {
            workbookService.changeValidStatus(producingResult.workbook.getId(), false);
            return new PlanApiData.WorkbookResult("#560.081 number of tasks for this workbook exceeded the allowed maximum number. Workbook was created but its status is 'not valid'. " +
                    "Allowed maximum number of tasks: " + globals.maxTasksPerPlan + ", tasks in this workbook:  " + countTasks.numberOfTasks);
        }

        PlanApiData.WorkbookResult result = new PlanApiData.WorkbookResult(plan);
        result.workbook = producingResult.workbook;

        workbookService.changeValidStatus(producingResult.workbook.getId(), true);

        return result;
    }

    public PlanApiData.PlansResult getPlans(Pageable pageable, boolean isArchive) {
        pageable = ControllerUtils.fixPageSize(globals.planRowsLimit, pageable);
        List<Plan> plans = planRepository.findAllByOrderByIdDesc();
        AtomicInteger count = new AtomicInteger();
        plans = plans.stream()
                .filter(o-> {
                    try {
                        PlanParamsYaml ppy = PlanParamsYamlUtils.BASE_YAML_UTILS.to(o.getParams());
                        boolean b = ppy.internalParams == null || !ppy.internalParams.archived;
                        b = isArchive != b;
                        if (b) {
                            count.incrementAndGet();
                        }
                        return b;
                    } catch (YAMLException e) {
                        log.error("#560.020 Can't parse Plan params. It's broken or unknown version. Plan id: #{}", o.getId());
                        log.error("#560.025 Params:\n{}", o.getParams());
                        log.error("#560.030 Error: {}", e.toString());
                        return false;
                    }
                })
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .peek(o-> o.setParams(null))
                .collect(Collectors.toList());

        PlanApiData.PlansResult plansResultRest = new PlanApiData.PlansResult();
        plansResultRest.items = new PageImpl<>(plans, pageable, count.get());

        return plansResultRest;
    }

    public PlanApiData.PlanResult getPlan(Long id) {
        final PlanImpl plan = planCache.findById(id);
        if (plan == null) {
            return new PlanApiData.PlanResult(
                    "#560.050 plan wasn't found, planId: " + id,
                    EnumsApi.PlanValidateStatus.PLAN_NOT_FOUND_ERROR );
        }
        return new PlanApiData.PlanResult(plan, plan.getParams());
    }

    public PlanApiData.PlanResult validatePlan(Long id) {
        final PlanImpl plan = planCache.findById(id);
        if (plan == null) {
            return new PlanApiData.PlanResult("#560.070 plan wasn't found, planId: " + id,
                    EnumsApi.PlanValidateStatus.PLAN_NOT_FOUND_ERROR );
        }

        PlanApiData.PlanResult result = new PlanApiData.PlanResult(plan, plan.getParams());
        PlanApiData.PlanValidation planValidation = planService.validateInternal(plan);
        result.errorMessages = planValidation.errorMessages;
        result.infoMessages = planValidation.infoMessages;
        result.status = planValidation.status;
        return result;
    }

    @SuppressWarnings("Duplicates")
    public PlanApiData.PlanResult addPlan(String planYamlAsStr) {
        if (StringUtils.isBlank(planYamlAsStr)) {
            return new PlanApiData.PlanResult("#560.090 plan yaml is empty");
        }

        PlanParamsYaml ppy;
        try {
            ppy = PlanParamsYamlUtils.BASE_YAML_UTILS.to(planYamlAsStr);
        } catch (WrongVersionOfYamlFileException e) {
            return new PlanApiData.PlanResult("#560.110 Error parsing yaml: " + e.getMessage());
        }

        final String code = ppy.planYaml.planCode;
        if (StringUtils.isBlank(code)) {
            return new PlanApiData.PlanResult("#560.130 code of plan is empty");
        }
        Plan f = planRepository.findByCode(code);
        if (f!=null) {
            return new PlanApiData.PlanResult("#560.150 plan with such code already exists, code: " + code);
        }

        PlanImpl plan = new PlanImpl();
        plan.createdOn = System.currentTimeMillis();
        plan.code = ppy.planYaml.planCode;
        // we have to preserve a format of planParamsYaml for compatibility with old snippets
        plan.setParams(planYamlAsStr);
        plan = planCache.save(plan);

        PlanApiData.PlanValidation planValidation = planService.validateInternal(plan);

        PlanApiData.PlanResult result = new PlanApiData.PlanResult(plan, plan.params );
        result.infoMessages = planValidation.infoMessages;
        result.errorMessages = planValidation.errorMessages;
        return result;
    }

    @SuppressWarnings("Duplicates")
    public PlanApiData.PlanResult updatePlan(Long planId, String planYamlAsStr) {
        PlanImpl plan = planCache.findById(planId);
        if (plan == null) {
            return new PlanApiData.PlanResult(
                    "#560.010 plan wasn't found, planId: " + planId,
                    EnumsApi.PlanValidateStatus.PLAN_NOT_FOUND_ERROR );
        }
        if (StringUtils.isBlank(planYamlAsStr)) {
            return new PlanApiData.PlanResult("#560.170 plan yaml is empty");
        }

        PlanParamsYaml ppy = PlanParamsYamlUtils.BASE_YAML_UTILS.to(planYamlAsStr);

        final String code = ppy.planYaml.planCode;
        if (StringUtils.isBlank(code)) {
            return new PlanApiData.PlanResult("#560.190 code of plan is empty");
        }
        if (StringUtils.isBlank(code)) {
            return new PlanApiData.PlanResult("#560.210 plan is empty");
        }
        Plan p = planRepository.findByCode(code);
        if (p!=null && !p.getId().equals(plan.getId())) {
            return new PlanApiData.PlanResult("#560.230 plan with such code already exists, code: " + code);
        }
        plan.code = code;
        // we have to preserve a format of planParamsYaml for compatibility with old snippets
        plan.setParams(planYamlAsStr);
        plan = planCache.save(plan);

        PlanApiData.PlanValidation planValidation = planService.validateInternal(plan);

        PlanApiData.PlanResult result = new PlanApiData.PlanResult(plan, plan.params );
        result.infoMessages = planValidation.infoMessages;
        result.errorMessages = planValidation.errorMessages;
        return result;
    }

    public OperationStatusRest deletePlanById(Long id) {
        Plan plan = planCache.findById(id);
        if (plan == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.250 plan wasn't found, planId: " + id);
        }
        planCache.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest archivePlanById(Long id) {
        PlanImpl plan = planCache.findById(id);
        if (plan == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.270 plan wasn't found, planId: " + id);
        }
        PlanParamsYaml ppy = PlanParamsYamlUtils.BASE_YAML_UTILS.to(plan.params);
        if (ppy.internalParams==null) {
            ppy.internalParams = new PlanApiData.PlanInternalParamsYaml();
        }
        ppy.internalParams.archived = true;
        plan.params = PlanParamsYamlUtils.BASE_YAML_UTILS.toString(ppy);

        planCache.save(plan);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest uploadPlan(MultipartFile file) {

        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.290 name of uploaded file is null");
        }
        String ext = StrUtils.getExtension(originFilename);
        if (ext==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.310 file without extension, bad filename: " + originFilename);
        }
        if (!StringUtils.equalsAny(ext.toLowerCase(), YAML_EXT, YML_EXT)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.330 only '.yml' and '.yaml' files are supported, filename: " + originFilename);
        }

        final String location = System.getProperty("java.io.tmpdir");

        try {
            File tempDir = DirUtils.createTempDir("mh-plan-upload-");
            if (tempDir==null || tempDir.isFile()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#560.350 can't create temporary directory in " + location);
            }
            final File planFile = new File(tempDir, "plans" + ext);
            log.debug("Start storing an uploaded plan to disk");
            try(OutputStream os = new FileOutputStream(planFile)) {
                IOUtils.copy(file.getInputStream(), os, 64000);
            }
            log.debug("Start loading plan into db");
            String yaml = FileUtils.readFileToString(planFile, StandardCharsets.UTF_8);
            PlanApiData.PlanResult result = addPlan(yaml);

            if (result.isErrorMessages()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, result.errorMessages, result.infoMessages);
            }
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        catch (Throwable e) {
            log.error("Error", e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.370 can't load plans, Error: " + e.toString());
        }
    }

    private List<String> toErrorMessages(List<PlanApiData.PlanStatus> statuses) {
        return statuses.stream().filter(o->!o.isOk).map(o->o.error).collect(Collectors.toList());
    }

    private boolean isError(List<PlanApiData.PlanStatus > statuses) {
        return statuses.stream().filter(o->!o.isOk).findFirst().orElse(null)!=null;
    }

    public static WorkbookParamsYaml asWorkbookParamsYaml(String poolCode) {
        WorkbookParamsYaml wpy = new WorkbookParamsYaml();
        wpy.workbookYaml.poolCodes.computeIfAbsent(Consts.WORKBOOK_INPUT_TYPE, o->new ArrayList<>()).add(poolCode);
//        return "poolCodes:\n  "+ Consts.WORKBOOK_INPUT_TYPE+":\n" +
//                "  - " + poolCode;
        return wpy;
    }

    // ========= Workbook specific =============

    public OperationStatusRest changeWorkbookExecState(String state, Long workbookId) {
        EnumsApi.WorkbookExecState execState = EnumsApi.WorkbookExecState.valueOf(state.toUpperCase());
        if (execState== EnumsApi.WorkbookExecState.UNKNOWN) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.070 Unknown exec state, state: " + state);
        }
        //noinspection UnnecessaryLocalVariable
        OperationStatusRest status = planService.workbookTargetExecState(workbookId, execState);
        return status;
    }

    public OperationStatusRest deleteWorkbookById(Long workbookId) {
        PlanApiData.WorkbookResult result = workbookService.getWorkbookExtended(workbookId);
        if (CollectionUtils.isNotEmpty(result.errorMessages)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, result.errorMessages);
        }

        Workbook wb = workbookCache.findById(workbookId);
        if (wb==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.084 Workbook wasn't found, workbookId: " + workbookId );
        }
        planService.deleteWorkbook(workbookId);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }


}
