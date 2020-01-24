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
import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.launchpad.data.PlanData;
import ai.metaheuristic.ai.launchpad.event.LaunchpadInternalEvent;
import ai.metaheuristic.ai.launchpad.repositories.PlanRepository;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookCache;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.plan.PlanApiData;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
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
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.List;
import java.util.Objects;
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
    private final ApplicationEventPublisher publisher;
    private final WorkbookCache workbookCache;

    public PlanApiData.WorkbookResult addWorkbook(Long planId, String poolCode, String inputResourceParams, LaunchpadContext context) {
        return getWorkbookResult(poolCode, inputResourceParams, context, planCache.findById(planId));
    }

    public PlanApiData.WorkbookResult addWorkbook(String planCode, String poolCode, String inputResourceParams, LaunchpadContext context) {
        return getWorkbookResult(poolCode, inputResourceParams, context, planRepository.findByCodeAndCompanyId(planCode, context.getCompanyId()));
    }

    private PlanApiData.WorkbookResult getWorkbookResult(String poolCode, String inputResourceParams, LaunchpadContext context, PlanImpl plan) {
        // validate the plan
        PlanApiData.PlanValidation planValidation = planService.validateInternal(plan);
        if (planValidation.status != EnumsApi.PlanValidateStatus.OK) {
            PlanApiData.WorkbookResult r = new PlanApiData.WorkbookResult();
            r.errorMessages = planValidation.errorMessages;
            return r;
        }

        OperationStatusRest status = checkPlan(plan, context);
        if (status != null) {
            return new PlanApiData.WorkbookResult( "#560.011 access denied: " + status.getErrorMessagesAsStr());
        }
        return workbookService.getAddWorkbookInternal(poolCode, inputResourceParams, plan);
    }

    public PlanApiData.PlansResult getPlans(Pageable pageable, boolean isArchive, LaunchpadContext context) {
        pageable = ControllerUtils.fixPageSize(globals.planRowsLimit, pageable);
        List<Plan> plans = planRepository.findAllByOrderByIdDesc(context.getCompanyId());
        AtomicInteger count = new AtomicInteger();

        List<Plan> activePlans = plans.stream()
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
                }).collect(Collectors.toList());

        plans = activePlans.stream()
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .peek(o-> o.setParams(null))
                .collect(Collectors.toList());

        PlanApiData.PlansResult plansResultRest = new PlanApiData.PlansResult();
        plansResultRest.items = new PageImpl<>(plans, pageable, count.get());
        plansResultRest.assetMode = globals.assetMode;

        return plansResultRest;
    }

    public PlanApiData.PlanResult getPlan(Long planId, LaunchpadContext context) {
        final PlanImpl plan = planCache.findById(planId);
        if (plan == null) {
            return new PlanApiData.PlanResult(
                    "#560.050 plan wasn't found, planId: " + planId,
                    EnumsApi.PlanValidateStatus.PLAN_NOT_FOUND_ERROR );
        }
        String yaml = plan.getPlanParamsYaml().originYaml;
        return new PlanApiData.PlanResult(plan, yaml);
    }

    public PlanApiData.PlanResult validatePlan(Long planId, LaunchpadContext context) {
        final PlanImpl plan = planCache.findById(planId);
        if (plan == null) {
            return new PlanApiData.PlanResult("#560.070 plan wasn't found, planId: " + planId,
                    EnumsApi.PlanValidateStatus.PLAN_NOT_FOUND_ERROR );
        }

        String yaml = plan.getPlanParamsYaml().originYaml;
        PlanApiData.PlanResult result = new PlanApiData.PlanResult(plan, yaml);
        PlanApiData.PlanValidation planValidation = planService.validateInternal(plan);
        result.errorMessages = planValidation.errorMessages;
        result.infoMessages = planValidation.infoMessages;
        result.status = planValidation.status;
        return result;
    }

    @SuppressWarnings("Duplicates")
    public PlanApiData.PlanResult addPlan(String planYamlAsStr, LaunchpadContext context) {
        if (globals.assetMode==EnumsApi.LaunchpadAssetMode.replicated) {
            return new PlanApiData.PlanResult("#560.085 Can't add a new plan while 'replicated' mode of asset is active");
        }
        if (StringUtils.isBlank(planYamlAsStr)) {
            return new PlanApiData.PlanResult("#560.090 plan yaml is empty");
        }

        PlanParamsYaml ppy;
        try {
            ppy = PlanParamsYamlUtils.BASE_YAML_UTILS.to(planYamlAsStr);
        } catch (WrongVersionOfYamlFileException e) {
            return new PlanApiData.PlanResult("#560.110 An error parsing yaml: " + e.getMessage());
        }

        final String code = ppy.planYaml.planCode;
        if (StringUtils.isBlank(code)) {
            return new PlanApiData.PlanResult("#560.130 the code of plan is empty");
        }
        Plan f = planRepository.findByCodeAndCompanyId(code, context.getCompanyId());
        if (f!=null) {
            return new PlanApiData.PlanResult("#560.150 the plan with such code already exists, code: " + code);
        }

        PlanImpl plan = new PlanImpl();
        ppy.originYaml = planYamlAsStr;
        ppy.internalParams.updatedOn = System.currentTimeMillis();
        String params = PlanParamsYamlUtils.BASE_YAML_UTILS.toString(ppy);
        plan.setParams(params);

        plan.companyId = context.getCompanyId();
        plan.createdOn = System.currentTimeMillis();
        plan.code = ppy.planYaml.planCode;
        plan = planCache.save(plan);

        PlanApiData.PlanValidation planValidation = planService.validateInternal(plan);

        PlanApiData.PlanResult result = new PlanApiData.PlanResult(plan, plan.getParams() );
        result.infoMessages = planValidation.infoMessages;
        result.errorMessages = planValidation.errorMessages;
        return result;
    }

    @SuppressWarnings("Duplicates")
    public PlanApiData.PlanResult updatePlan(Long planId, String planYamlAsStr, LaunchpadContext context) {
        if (globals.assetMode==EnumsApi.LaunchpadAssetMode.replicated) {
            return new PlanApiData.PlanResult("#560.160 Can't update a plan while 'replicated' mode of asset is active");
        }
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
        Plan p = planRepository.findByCodeAndCompanyId(code, context.getCompanyId());
        if (p!=null && !p.getId().equals(plan.getId())) {
            return new PlanApiData.PlanResult("#560.230 plan with such code already exists, code: " + code);
        }
        plan.code = code;

        ppy.originYaml = planYamlAsStr;
        ppy.internalParams.updatedOn = System.currentTimeMillis();
        String params = PlanParamsYamlUtils.BASE_YAML_UTILS.toString(ppy);
        plan.setParams(params);

        plan = planCache.save(plan);

        PlanApiData.PlanValidation planValidation = planService.validateInternal(plan);

        PlanApiData.PlanResult result = new PlanApiData.PlanResult(plan, plan.getParams() );
        result.infoMessages = planValidation.infoMessages;
        result.errorMessages = planValidation.errorMessages;
        return result;
    }

    public OperationStatusRest deletePlanById(Long planId, LaunchpadContext context) {
        if (globals.assetMode==EnumsApi.LaunchpadAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.240 Can't delete a plan while 'replicated' mode of asset is active");
        }
        Plan plan = planCache.findById(planId);
        if (plan == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.250 plan wasn't found, planId: " + planId);
        }
        planCache.deleteById(planId);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest archivePlanById(Long id, LaunchpadContext context) {
        if (globals.assetMode==EnumsApi.LaunchpadAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.260 Can't archive a plan while 'replicated' mode of asset is active");
        }
        PlanImpl plan = planCache.findById(id);
        OperationStatusRest status = checkPlan(plan, context);
        if (status!=null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#560.270 plan wasn't found, planId: " + id+", " + status.getErrorMessagesAsStr());
        }
        PlanParamsYaml ppy = PlanParamsYamlUtils.BASE_YAML_UTILS.to(plan.getParams());
        if (ppy.internalParams==null) {
            ppy.internalParams = new PlanParamsYaml.InternalParams();
        }
        ppy.internalParams.archived = true;
        ppy.internalParams.updatedOn = System.currentTimeMillis();
        plan.setParams(PlanParamsYamlUtils.BASE_YAML_UTILS.toString(ppy));

        planCache.save(plan);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest uploadPlan(MultipartFile file, LaunchpadContext context) {
        if (globals.assetMode==EnumsApi.LaunchpadAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.280 Can't upload plan while 'replicated' mode of asset is active");
        }

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

        File tempDir=null;
        try {
            tempDir = DirUtils.createTempDir("mh-plan-upload-");
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
            PlanApiData.PlanResult result = addPlan(yaml, context);

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
        finally {
            DirUtils.deleteAsync(tempDir);
        }
    }

    // ========= Workbook specific =============

    public OperationStatusRest changeWorkbookExecState(String state, Long workbookId, LaunchpadContext context) {
        EnumsApi.WorkbookExecState execState = EnumsApi.WorkbookExecState.valueOf(state.toUpperCase());
        if (execState==EnumsApi.WorkbookExecState.UNKNOWN) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.390 Unknown exec state, state: " + state);
        }
        OperationStatusRest status = checkWorkbook(workbookId, context);
        if (status != null) {
            return status;
        }
        status = planService.workbookTargetExecState(workbookId, execState);
        return status;
    }

    public OperationStatusRest deleteWorkbookById(Long workbookId, LaunchpadContext context) {
        OperationStatusRest status = checkWorkbook(workbookId, context);
        if (status != null) {
            return status;
        }
        publisher.publishEvent( new LaunchpadInternalEvent.WorkbookDeletionEvent(this, workbookId) );
        workbookService.deleteWorkbook(workbookId, context.getCompanyId());

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    private OperationStatusRest checkWorkbook(Long workbookId, LaunchpadContext context) {
        if (workbookId==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.395 workbookId is null");
        }
        Workbook wb = workbookCache.findById(workbookId);
        if (wb==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.400 Workbook wasn't found, workbookId: " + workbookId );
        }
        PlanData.PlansForCompany plansForCompany = planService.getPlan(context.getCompanyId(), wb.getPlanId());
        if (plansForCompany.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.405 Plan wasn't found, " +
                    "companyId: "+context.getCompanyId()+", planId: " + wb.getPlanId()+", workbookId: " + wb.getId()+", error msg: " + plansForCompany.getErrorMessagesAsStr() );
        }
        return null;
    }

    private OperationStatusRest checkPlan(Plan plan, LaunchpadContext context) {
        if (plan==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.395 plan is null");
        }
        if (!Objects.equals(plan.getCompanyId(), context.getCompanyId())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.405 Access to plan is denied, planId: " + plan.getId() );
        }
        return null;
    }
}
