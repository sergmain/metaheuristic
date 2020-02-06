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

package ai.metaheuristic.ai.launchpad.plan;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Monitoring;
import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.beans.Company;
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.ai.launchpad.variable.SimpleVariableAndStorageUrl;
import ai.metaheuristic.ai.launchpad.company.CompanyCache;
import ai.metaheuristic.ai.launchpad.data.PlanData;
import ai.metaheuristic.ai.launchpad.event.LaunchpadInternalEvent;
import ai.metaheuristic.ai.launchpad.repositories.PlanRepository;
import ai.metaheuristic.ai.launchpad.repositories.SnippetRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookFSM;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYaml;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYamlUtils;
import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.YamlVersion;
import ai.metaheuristic.api.data.plan.PlanApiData;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.api.launchpad.Plan;
import ai.metaheuristic.api.launchpad.Workbook;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.yaml.versioning.YamlForVersioning;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.metaheuristic.api.EnumsApi.PlanValidateStatus.OK;

@Service
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class PlanService {

    private final WorkbookRepository workbookRepository;
    private final PlanCache planCache;
    private final PlanRepository planRepository;

    private final WorkbookService workbookService;
    private final CommonProcessValidatorService commonProcessValidatorService;
    private final SnippetRepository snippetRepository;
    private final WorkbookFSM workbookFSM;
    private final CompanyCache companyCache;

    @Async
    @EventListener
    public void handleAsync(LaunchpadInternalEvent.PlanLockingEvent event) {
        setLockedTo(event.planId, event.companyUniqueId, event.lock);
    }

    public PlanData.PlansForCompany getAvailablePlansForCompany(LaunchpadContext context) {
        return getAvailablePlansForCompany(context.getCompanyId());
    }

    public PlanData.PlansForCompany getPlan(Long companyId, Long planId) {
        PlanData.PlansForCompany availablePlansForCompany = getAvailablePlansForCompany(companyId, (o) -> o.getId().equals(planId));
        if (availablePlansForCompany.items.size()>1) {
            log.error("!!!!!!!!!!!!!!!! error in code -  (plansForBatchResult.items.size()>1) !!!!!!!!!!!!!!!!!!!!!!!!!");
        }
        return availablePlansForCompany;
    }

    public PlanData.PlansForCompany getAvailablePlansForCompany(Long companyId) {
        return getAvailablePlansForCompany(companyId, (f) -> true);
    }

    private PlanData.PlansForCompany getAvailablePlansForCompany(Long companyUniqueId, final Function<Plan, Boolean> planFilter) {
        final PlanData.PlansForCompany plans = new PlanData.PlansForCompany();
        plans.items = planRepository.findAllAsPlan(companyUniqueId).stream().filter(planFilter::apply).filter(o->{
            if (!o.isValid()) {
                return false;
            }
            try {
                PlanParamsYaml ppy = PlanParamsYamlUtils.BASE_YAML_UTILS.to(o.getParams());
                return ppy.internalParams == null || !ppy.internalParams.archived;
            } catch (YAMLException e) {
                final String es = "#995.010 Can't parse Plan params. It's broken or unknown version. Plan id: #" + o.getId();
                plans.addErrorMessage(es);
                log.error(es);
                log.error("#995.015 Params:\n{}", o.getParams());
                log.error("#995.020 Error: {}", e.toString());
                return false;
            }
        }).collect(Collectors.toList());

        Company company = companyCache.findByUniqueId(companyUniqueId);
        if (!S.b(company.getParams())) {
            final Set<String> groups = new HashSet<>();
            try {
                CompanyParamsYaml cpy = CompanyParamsYamlUtils.BASE_YAML_UTILS.to(company.getParams());
                if (cpy.ac!=null && !S.b(cpy.ac.groups)) {
                    String[] arr = StringUtils.split(cpy.ac.groups, ',');
                    Stream.of(arr).forEach(s-> groups.add(s.strip()));
                }
            } catch (YAMLException e) {
                final String es = "#995.025 Can't parse Company params. It's broken or version is unknown. Company companyUniqueId: #" + companyUniqueId;
                plans.addErrorMessage(es);
                log.error(es);
                log.error("#995.027 Params:\n{}", company.getParams());
                log.error("#995.030 Error: {}", e.toString());
                return plans;
            }

            if (!groups.isEmpty()) {
                List<Plan> commonPlans = planRepository.findAllAsPlan(Consts.ID_1).stream().filter(planFilter::apply).filter(o -> {
                    if (!o.isValid()) {
                        return false;
                    }
                    try {
                        PlanParamsYaml ppy = PlanParamsYamlUtils.BASE_YAML_UTILS.to(o.getParams());
                        if (ppy.plan.ac!=null) {
                            String[] arr = StringUtils.split(ppy.plan.ac.groups, ',');
                            return Stream.of(arr).map(String::strip).anyMatch(groups::contains);
                        }
                        return false;
                    } catch (YAMLException e) {
                        final String es = "#995.033 Can't parse Plan params. It's broken or unknown version. Plan id: #" + o.getId();
                        plans.addErrorMessage(es);
                        log.error(es);
                        log.error("#995.035 Params:\n{}", o.getParams());
                        log.error("#995.037 Error: {}", e.toString());
                        return false;
                    }
                }).collect(Collectors.toList());
                plans.items.addAll(commonPlans);
            }
        }
        plans.items.sort((o1, o2) -> Long.compare(o2.getId(), o1.getId()));

        return plans;
    }

    // TODO 2019.05.19 add reporting of producing of tasks
    // TODO 2020.01.17 reporting to where? do we need to implement it?
    public synchronized void createAllTasks() {

        Monitoring.log("##019", Enums.Monitor.MEMORY);
        List<WorkbookImpl> workbooks = workbookRepository.findByExecState(EnumsApi.WorkbookExecState.PRODUCING.code);
        Monitoring.log("##020", Enums.Monitor.MEMORY);
        if (!workbooks.isEmpty()) {
            log.info("#701.020 Start producing tasks");
        }
        for (WorkbookImpl workbook : workbooks) {
            PlanImpl plan = planCache.findById(workbook.getPlanId());
            if (plan==null) {
                workbookFSM.toStopped(workbook.id);
                continue;
            }
            Monitoring.log("##021", Enums.Monitor.MEMORY);
            log.info("#701.030 Producing tasks for plan.code: {}, input resource pool: \n{}",plan.code, workbook.getParams());
            produceAllTasks(true, plan, workbook);
            Monitoring.log("##022", Enums.Monitor.MEMORY);
        }
        if (!workbooks.isEmpty()) {
            log.info("#701.040 Producing of tasks was finished");
        }
    }

    public PlanApiData.PlanValidation validateInternal(PlanImpl plan) {
        PlanApiData.PlanValidation planValidation = getPlanValidation(plan);
        setValidTo(plan, planValidation.status == EnumsApi.PlanValidateStatus.OK );
        if (plan.isValid() || planValidation.status==OK) {
            if (plan.isValid() && planValidation.status!=OK) {
                log.error("#701.097 Need to investigate: (plan.isValid() && planValidation.status!=OK)");
            }
            planValidation.infoMessages = Collections.singletonList("Validation result: OK");
        }
        else {
            final String es = "#701.100 Validation error: " + planValidation.status;
            log.error(es);
            planValidation.addErrorMessage(es);
        }
        return planValidation;
    }

    private PlanApiData.PlanValidation getPlanValidation(PlanImpl plan) {
        final PlanApiData.PlanValidation planValidation = new PlanApiData.PlanValidation();
        try {
            planValidation.status = validate(plan);
        } catch (YAMLException e) {
            planValidation.addErrorMessage("#701.090 Error while parsing yaml config, " + e.toString());
            planValidation.status = EnumsApi.PlanValidateStatus.YAML_PARSING_ERROR;
        }
        return planValidation;
    }

    private final static Object syncObj = new Object();

    private void setValidTo(Plan plan, boolean valid) {
        synchronized (syncObj) {
            PlanImpl p = planRepository.findByIdForUpdate(plan.getId(), plan.getCompanyId());
            if (p!=null && p.isValid()!=valid) {
                p.setValid(valid);
                saveInternal(p);
            }
            plan.setValid(valid);
        }
    }

    private void setLockedTo(Long planId, Long companyUniqueId, boolean locked) {
        synchronized (syncObj) {
            PlanImpl p = planRepository.findByIdForUpdate(planId, companyUniqueId);
            if (p!=null && p.isLocked()!=locked) {
                p.setLocked(locked);
                saveInternal(p);
            }
        }
    }

    private void saveInternal(PlanImpl plan) {
        PlanParamsYaml ppy = PlanParamsYamlUtils.BASE_YAML_UTILS.to(plan.getParams());
        if (ppy.internalParams==null) {
            ppy.internalParams = new PlanParamsYaml.InternalParams();
        }
        ppy.internalParams.updatedOn = System.currentTimeMillis();
        String params = PlanParamsYamlUtils.BASE_YAML_UTILS.toString(ppy);
        plan.setParams(params);

        planCache.save(plan);
    }

    public PlanApiData.TaskProducingResultComplex produceAllTasks(boolean isPersist, PlanImpl plan, Workbook workbook ) {
        PlanApiData.TaskProducingResultComplex result = new PlanApiData.TaskProducingResultComplex();
        if (isPersist && workbook.getExecState()!= EnumsApi.WorkbookExecState.PRODUCING.code) {
            result.planValidateStatus = EnumsApi.PlanValidateStatus.ALREADY_PRODUCED_ERROR;
            return result;
        }
        long mills = System.currentTimeMillis();
        result.planValidateStatus = validate(plan);
        log.info("#701.150 Plan was validated for "+(System.currentTimeMillis() - mills) + " ms.");
        if (result.planValidateStatus != EnumsApi.PlanValidateStatus.OK &&
                result.planValidateStatus != EnumsApi.PlanValidateStatus.EXPERIMENT_ALREADY_STARTED_ERROR ) {
            log.error("#701.160 Can't produce tasks, error: {}", result.planValidateStatus);
            if(isPersist) {
                workbookFSM.toStopped(workbook.getId());
            }
            return result;
        }
        Monitoring.log("##022", Enums.Monitor.MEMORY);
        mills = System.currentTimeMillis();
        result = workbookService.produceTasks(isPersist, plan, workbook.getId());
        log.info("#701.170 PlanService.produceTasks() was processed for "+(System.currentTimeMillis() - mills) + " ms.");
        Monitoring.log("##033", Enums.Monitor.MEMORY);

        return result;
    }

    public EnumsApi.PlanValidateStatus validate(PlanImpl plan) {
        if (plan==null) {
            return EnumsApi.PlanValidateStatus.NO_ANY_PROCESSES_ERROR;
        }
        if (StringUtils.isBlank(plan.getCode())) {
            return EnumsApi.PlanValidateStatus.PLAN_CODE_EMPTY_ERROR;
        }
        if (StringUtils.isBlank(plan.getParams())) {
            return EnumsApi.PlanValidateStatus.PLAN_PARAMS_EMPTY_ERROR;
        }
        PlanParamsYaml planParams = plan.getPlanParamsYaml();
        PlanParamsYaml.PlanYaml planYaml = planParams.plan;
        if (planYaml.getProcesses().isEmpty()) {
            return EnumsApi.PlanValidateStatus.NO_ANY_PROCESSES_ERROR;
        }

        PlanParamsYaml.Process lastProcess = null;
        List<PlanParamsYaml.Process> processes = planYaml.getProcesses();
        for (int i = 0; i < processes.size(); i++) {
            PlanParamsYaml.Process process = processes.get(i);
            if (i + 1 < processes.size()) {
                if (process.output.isEmpty()) {
                    return EnumsApi.PlanValidateStatus.PROCESS_PARAMS_EMPTY_ERROR;
                }
                for (PlanParamsYaml.Variable params : process.output) {
                    if (S.b(params.name)) {
                        return EnumsApi.PlanValidateStatus.OUTPUT_VARIABLE_NOT_DEFINED_ERROR;
                    }
                    if (params.sourcing==null) {
                        return EnumsApi.PlanValidateStatus.SOURCING_OF_VARIABLE_NOT_DEFINED_ERROR;
                    }
                }
            }
            lastProcess = process;
            if (S.b(process.code) || !StrUtils.isCodeOk(process.code)){
                log.error("Error while validating plan {}", planYaml);
                return EnumsApi.PlanValidateStatus.PROCESS_CODE_CONTAINS_ILLEGAL_CHAR_ERROR;
            }
            EnumsApi.PlanValidateStatus status;
            status = checkSnippets(plan, process);
            if (status!=OK) {
                return status;
            }
        }
        return EnumsApi.PlanValidateStatus.OK;
    }

    private EnumsApi.PlanValidateStatus checkSnippets(Plan plan, PlanParamsYaml.Process process) {
        YamlVersion v = YamlForVersioning.getYamlForVersion().load(plan.getParams());

        if (process.snippet!=null) {
            PlanParamsYaml.SnippetDefForPlan snDef = process.snippet;
            if (snDef.context==EnumsApi.SnippetExecContext.internal) {
                if (!Consts.MH_INTERNAL_SNIPPETS.contains(snDef.code)) {
                    return EnumsApi.PlanValidateStatus.INTERNAL_SNIPPET_NOT_FOUND_ERROR;
                }
            }
            else {
                EnumsApi.PlanValidateStatus x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x != OK) {
                    log.error("#177.030 Snippet wasn't found for code: {}, process: {}", snDef.code, process);
                    return x;
                }
            }
        }
        if (process.preSnippets!=null) {
            for (PlanParamsYaml.SnippetDefForPlan snDef : process.preSnippets) {
                EnumsApi.PlanValidateStatus x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x != OK) {
                    log.error("#177.030 Pre-snippet {} wasn't found", snDef.code);
                    return x;
                }
            }
        }
        if (process.postSnippets!=null) {
            for (PlanParamsYaml.SnippetDefForPlan snDef : process.postSnippets) {
                EnumsApi.PlanValidateStatus x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x != OK) {
                    log.error("#177.030 Post-snippet {} wasn't found", snDef.code);
                    return x;
                }
            }
        }

        return OK;
    }

    private EnumsApi.PlanValidateStatus checkRequiredVersionOfTaskParams(int planParamsVersion, PlanParamsYaml.Process process, PlanParamsYaml.SnippetDefForPlan snDef) {
        if (StringUtils.isNotBlank(snDef.code)) {
            Long  snippetId = snippetRepository.findIdByCode(snDef.code);
            if (snippetId == null) {
                log.error("#177.030 snippet wasn't found for code: {}, process: {}", snDef.code, process);
                return EnumsApi.PlanValidateStatus.SNIPPET_NOT_FOUND_ERROR;
            }
        }
        else {
            log.error("#177.060 snippet wasn't found for code: {}, process: {}", snDef.code, process);
            return EnumsApi.PlanValidateStatus.SNIPPET_NOT_FOUND_ERROR;
        }
        if (!commonProcessValidatorService.checkRequiredVersion(planParamsVersion, snDef)) {
            return EnumsApi.PlanValidateStatus.VERSION_OF_SNIPPET_IS_TOO_LOW_ERROR;
        }
        return OK;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProduceTaskResult {
        public EnumsApi.PlanProducingStatus status;
        public List<String> outputResourceCodes;
        public int numberOfTasks=0;
        public List<Long> taskIds = new ArrayList<>();

        public ProduceTaskResult(EnumsApi.PlanProducingStatus status) {
            this.status = status;
        }
    }

    @Data
    @NoArgsConstructor
    public static class ResourcePools {
        public final Map<String, List<String>> collectedInputs = new HashMap<>();
        public Map<String, PlanParamsYaml.Variable> inputStorageUrls=null;
        public final Map<String, String> mappingCodeToOriginalFilename = new HashMap<>();
        public EnumsApi.PlanProducingStatus status = EnumsApi.PlanProducingStatus.OK;

        public ResourcePools(List<SimpleVariableAndStorageUrl> initialInputResourceCodes) {

            if (initialInputResourceCodes==null || initialInputResourceCodes.isEmpty()) {
                status = EnumsApi.PlanProducingStatus.INPUT_POOL_CODE_DOESNT_EXIST_ERROR;
                return;
            }

            initialInputResourceCodes.forEach(o->
                collectedInputs.computeIfAbsent(o.variable, p -> new ArrayList<>()).add(o.id)
            );

            initialInputResourceCodes.forEach(o-> mappingCodeToOriginalFilename.put(o.id, o.originalFilename));

            inputStorageUrls = initialInputResourceCodes.stream()
                    .collect(Collectors.toMap(o -> o.id, o -> {
                        DataStorageParams p = o.getParams();
                        return new PlanParamsYaml.Variable(p.sourcing, p.git, p.disk, p.storageType);
                    }));

        }

        public void clean() {
            collectedInputs.values().forEach(o-> o.forEach(inputStorageUrls::remove));
            collectedInputs.clear();
            mappingCodeToOriginalFilename.clear();
        }

        public void add(String outputType, List<String> outputResourceCodes) {
            if (outputResourceCodes!=null) {
                collectedInputs.computeIfAbsent(outputType, k -> new ArrayList<>()).addAll(outputResourceCodes);
            }
        }

        public void merge(ResourcePools metaPools) {
            metaPools.collectedInputs.forEach((key, value) -> collectedInputs.merge(
                    key, value, (o, o1) -> {o.addAll(o1); return o;} )
            );
            inputStorageUrls.putAll(metaPools.inputStorageUrls);
            mappingCodeToOriginalFilename.putAll((metaPools.mappingCodeToOriginalFilename));
        }
    }

}
