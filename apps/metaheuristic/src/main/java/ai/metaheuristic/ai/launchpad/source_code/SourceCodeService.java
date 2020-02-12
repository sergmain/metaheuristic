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

package ai.metaheuristic.ai.launchpad.source_code;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Monitoring;
import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.beans.Company;
import ai.metaheuristic.ai.launchpad.beans.ExecContextImpl;
import ai.metaheuristic.ai.launchpad.beans.SourceCodeImpl;
import ai.metaheuristic.ai.launchpad.variable.SimpleVariableAndStorageUrl;
import ai.metaheuristic.ai.launchpad.company.CompanyCache;
import ai.metaheuristic.ai.launchpad.data.SourceCodeData;
import ai.metaheuristic.ai.launchpad.event.LaunchpadInternalEvent;
import ai.metaheuristic.ai.launchpad.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.launchpad.repositories.SnippetRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookFSM;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYaml;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.PlanParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.YamlVersion;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.api.launchpad.SourceCode;
import ai.metaheuristic.api.launchpad.ExecContext;
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

import static ai.metaheuristic.api.EnumsApi.SourceCodeValidateStatus.OK;

@Service
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class SourceCodeService {

    private final WorkbookRepository workbookRepository;
    private final SourceCodeCache sourceCodeCache;
    private final SourceCodeRepository sourceCodeRepository;

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

    public SourceCodeData.PlansForCompany getAvailablePlansForCompany(LaunchpadContext context) {
        return getAvailablePlansForCompany(context.getCompanyId());
    }

    public SourceCodeData.PlansForCompany getPlan(Long companyId, Long planId) {
        SourceCodeData.PlansForCompany availablePlansForCompany = getAvailablePlansForCompany(companyId, (o) -> o.getId().equals(planId));
        if (availablePlansForCompany.items.size()>1) {
            log.error("!!!!!!!!!!!!!!!! error in code -  (plansForBatchResult.items.size()>1) !!!!!!!!!!!!!!!!!!!!!!!!!");
        }
        return availablePlansForCompany;
    }

    public SourceCodeData.PlansForCompany getAvailablePlansForCompany(Long companyId) {
        return getAvailablePlansForCompany(companyId, (f) -> true);
    }

    private SourceCodeData.PlansForCompany getAvailablePlansForCompany(Long companyUniqueId, final Function<SourceCode, Boolean> planFilter) {
        final SourceCodeData.PlansForCompany plans = new SourceCodeData.PlansForCompany();
        plans.items = sourceCodeRepository.findAllAsSourceCode(companyUniqueId).stream().filter(planFilter::apply).filter(o->{
            if (!o.isValid()) {
                return false;
            }
            try {
                SourceCodeParamsYaml ppy = PlanParamsYamlUtils.BASE_YAML_UTILS.to(o.getParams());
                return ppy.internalParams == null || !ppy.internalParams.archived;
            } catch (YAMLException e) {
                final String es = "#995.010 Can't parse SourceCode params. It's broken or unknown version. SourceCode id: #" + o.getId();
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
                List<SourceCode> commonSourceCodes = sourceCodeRepository.findAllAsSourceCode(Consts.ID_1).stream().filter(planFilter::apply).filter(o -> {
                    if (!o.isValid()) {
                        return false;
                    }
                    try {
                        SourceCodeParamsYaml ppy = PlanParamsYamlUtils.BASE_YAML_UTILS.to(o.getParams());
                        if (ppy.source.ac!=null) {
                            String[] arr = StringUtils.split(ppy.source.ac.groups, ',');
                            return Stream.of(arr).map(String::strip).anyMatch(groups::contains);
                        }
                        return false;
                    } catch (YAMLException e) {
                        final String es = "#995.033 Can't parse SourceCode params. It's broken or unknown version. SourceCode id: #" + o.getId();
                        plans.addErrorMessage(es);
                        log.error(es);
                        log.error("#995.035 Params:\n{}", o.getParams());
                        log.error("#995.037 Error: {}", e.toString());
                        return false;
                    }
                }).collect(Collectors.toList());
                plans.items.addAll(commonSourceCodes);
            }
        }
        plans.items.sort((o1, o2) -> Long.compare(o2.getId(), o1.getId()));

        return plans;
    }

    // TODO 2019.05.19 add reporting of producing of tasks
    // TODO 2020.01.17 reporting to where? do we need to implement it?
    public synchronized void createAllTasks() {

        Monitoring.log("##019", Enums.Monitor.MEMORY);
        List<ExecContextImpl> workbooks = workbookRepository.findByExecState(EnumsApi.WorkbookExecState.PRODUCING.code);
        Monitoring.log("##020", Enums.Monitor.MEMORY);
        if (!workbooks.isEmpty()) {
            log.info("#701.020 Start producing tasks");
        }
        for (ExecContextImpl workbook : workbooks) {
            SourceCodeImpl plan = sourceCodeCache.findById(workbook.getPlanId());
            if (plan==null) {
                workbookFSM.toStopped(workbook.id);
                continue;
            }
            Monitoring.log("##021", Enums.Monitor.MEMORY);
            log.info("#701.030 Producing tasks for sourceCode.code: {}, input resource pool: \n{}",plan.uid, workbook.getParams());
            produceAllTasks(true, plan, workbook);
            Monitoring.log("##022", Enums.Monitor.MEMORY);
        }
        if (!workbooks.isEmpty()) {
            log.info("#701.040 Producing of tasks was finished");
        }
    }

    public SourceCodeApiData.PlanValidation validateInternal(SourceCodeImpl plan) {
        SourceCodeApiData.PlanValidation planValidation = getPlanValidation(plan);
        setValidTo(plan, planValidation.status == EnumsApi.SourceCodeValidateStatus.OK );
        if (plan.isValid() || planValidation.status==OK) {
            if (plan.isValid() && planValidation.status!=OK) {
                log.error("#701.097 Need to investigate: (sourceCode.isValid() && planValidation.status!=OK)");
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

    private SourceCodeApiData.PlanValidation getPlanValidation(SourceCodeImpl plan) {
        final SourceCodeApiData.PlanValidation planValidation = new SourceCodeApiData.PlanValidation();
        try {
            planValidation.status = validate(plan);
        } catch (YAMLException e) {
            planValidation.addErrorMessage("#701.090 Error while parsing yaml config, " + e.toString());
            planValidation.status = EnumsApi.SourceCodeValidateStatus.YAML_PARSING_ERROR;
        }
        return planValidation;
    }

    private final static Object syncObj = new Object();

    private void setValidTo(SourceCode sourceCode, boolean valid) {
        synchronized (syncObj) {
            SourceCodeImpl p = sourceCodeRepository.findByIdForUpdate(sourceCode.getId(), sourceCode.getCompanyId());
            if (p!=null && p.isValid()!=valid) {
                p.setValid(valid);
                saveInternal(p);
            }
            sourceCode.setValid(valid);
        }
    }

    private void setLockedTo(Long planId, Long companyUniqueId, boolean locked) {
        synchronized (syncObj) {
            SourceCodeImpl p = sourceCodeRepository.findByIdForUpdate(planId, companyUniqueId);
            if (p!=null && p.isLocked()!=locked) {
                p.setLocked(locked);
                saveInternal(p);
            }
        }
    }

    private void saveInternal(SourceCodeImpl plan) {
        SourceCodeParamsYaml ppy = PlanParamsYamlUtils.BASE_YAML_UTILS.to(plan.getParams());
        if (ppy.internalParams==null) {
            ppy.internalParams = new SourceCodeParamsYaml.InternalParams();
        }
        ppy.internalParams.updatedOn = System.currentTimeMillis();
        String params = PlanParamsYamlUtils.BASE_YAML_UTILS.toString(ppy);
        plan.setParams(params);

        sourceCodeCache.save(plan);
    }

    public SourceCodeApiData.TaskProducingResultComplex produceAllTasks(boolean isPersist, SourceCodeImpl plan, ExecContext execContext) {
        SourceCodeApiData.TaskProducingResultComplex result = new SourceCodeApiData.TaskProducingResultComplex();
        if (isPersist && execContext.getExecState()!= EnumsApi.WorkbookExecState.PRODUCING.code) {
            result.sourceCodeValidateStatus = EnumsApi.SourceCodeValidateStatus.ALREADY_PRODUCED_ERROR;
            return result;
        }
        long mills = System.currentTimeMillis();
        result.sourceCodeValidateStatus = validate(plan);
        log.info("#701.150 SourceCode was validated for "+(System.currentTimeMillis() - mills) + " ms.");
        if (result.sourceCodeValidateStatus != EnumsApi.SourceCodeValidateStatus.OK &&
                result.sourceCodeValidateStatus != EnumsApi.SourceCodeValidateStatus.EXPERIMENT_ALREADY_STARTED_ERROR ) {
            log.error("#701.160 Can't produce tasks, error: {}", result.sourceCodeValidateStatus);
            if(isPersist) {
                workbookFSM.toStopped(execContext.getId());
            }
            return result;
        }
        Monitoring.log("##022", Enums.Monitor.MEMORY);
        mills = System.currentTimeMillis();
        result = workbookService.produceTasks(isPersist, plan, execContext.getId());
        log.info("#701.170 PlanService.produceTasks() was processed for "+(System.currentTimeMillis() - mills) + " ms.");
        Monitoring.log("##033", Enums.Monitor.MEMORY);

        return result;
    }

    public EnumsApi.SourceCodeValidateStatus validate(SourceCodeImpl plan) {
        if (plan==null) {
            return EnumsApi.SourceCodeValidateStatus.NO_ANY_PROCESSES_ERROR;
        }
        if (StringUtils.isBlank(plan.uid)) {
            return EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_UID_EMPTY_ERROR;
        }
        if (StringUtils.isBlank(plan.getParams())) {
            return EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_PARAMS_EMPTY_ERROR;
        }
        SourceCodeParamsYaml planParams = plan.getPlanParamsYaml();
        SourceCodeParamsYaml.SourceCodeYaml sourceCodeYaml = planParams.source;
        if (sourceCodeYaml.getProcesses().isEmpty()) {
            return EnumsApi.SourceCodeValidateStatus.NO_ANY_PROCESSES_ERROR;
        }

        SourceCodeParamsYaml.Process lastProcess = null;
        List<SourceCodeParamsYaml.Process> processes = sourceCodeYaml.getProcesses();
        for (int i = 0; i < processes.size(); i++) {
            SourceCodeParamsYaml.Process process = processes.get(i);
            if (i + 1 < processes.size()) {
                if (process.output.isEmpty()) {
                    return EnumsApi.SourceCodeValidateStatus.PROCESS_PARAMS_EMPTY_ERROR;
                }
                for (SourceCodeParamsYaml.Variable params : process.output) {
                    if (S.b(params.name)) {
                        return EnumsApi.SourceCodeValidateStatus.OUTPUT_VARIABLE_NOT_DEFINED_ERROR;
                    }
                    if (params.sourcing==null) {
                        return EnumsApi.SourceCodeValidateStatus.SOURCING_OF_VARIABLE_NOT_DEFINED_ERROR;
                    }
                }
            }
            lastProcess = process;
            if (S.b(process.code) || !StrUtils.isCodeOk(process.code)){
                log.error("Error while validating sourceCode {}", sourceCodeYaml);
                return EnumsApi.SourceCodeValidateStatus.PROCESS_CODE_CONTAINS_ILLEGAL_CHAR_ERROR;
            }
            EnumsApi.SourceCodeValidateStatus status;
            status = checkSnippets(plan, process);
            if (status!=OK) {
                return status;
            }
        }
        return EnumsApi.SourceCodeValidateStatus.OK;
    }

    private EnumsApi.SourceCodeValidateStatus checkSnippets(SourceCode sourceCode, SourceCodeParamsYaml.Process process) {
        YamlVersion v = YamlForVersioning.getYamlForVersion().load(sourceCode.getParams());

        if (process.snippet!=null) {
            SourceCodeParamsYaml.SnippetDefForSourceCode snDef = process.snippet;
            if (snDef.context==EnumsApi.SnippetExecContext.internal) {
                if (!Consts.MH_INTERNAL_SNIPPETS.contains(snDef.code)) {
                    return EnumsApi.SourceCodeValidateStatus.INTERNAL_SNIPPET_NOT_FOUND_ERROR;
                }
            }
            else {
                EnumsApi.SourceCodeValidateStatus x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x != OK) {
                    log.error("#177.030 Snippet wasn't found for code: {}, process: {}", snDef.code, process);
                    return x;
                }
            }
        }
        if (process.preSnippets!=null) {
            for (SourceCodeParamsYaml.SnippetDefForSourceCode snDef : process.preSnippets) {
                EnumsApi.SourceCodeValidateStatus x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x != OK) {
                    log.error("#177.030 Pre-snippet {} wasn't found", snDef.code);
                    return x;
                }
            }
        }
        if (process.postSnippets!=null) {
            for (SourceCodeParamsYaml.SnippetDefForSourceCode snDef : process.postSnippets) {
                EnumsApi.SourceCodeValidateStatus x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x != OK) {
                    log.error("#177.030 Post-snippet {} wasn't found", snDef.code);
                    return x;
                }
            }
        }

        return OK;
    }

    private EnumsApi.SourceCodeValidateStatus checkRequiredVersionOfTaskParams(int planParamsVersion, SourceCodeParamsYaml.Process process, SourceCodeParamsYaml.SnippetDefForSourceCode snDef) {
        if (StringUtils.isNotBlank(snDef.code)) {
            Long  snippetId = snippetRepository.findIdByCode(snDef.code);
            if (snippetId == null) {
                log.error("#177.030 snippet wasn't found for code: {}, process: {}", snDef.code, process);
                return EnumsApi.SourceCodeValidateStatus.SNIPPET_NOT_FOUND_ERROR;
            }
        }
        else {
            log.error("#177.060 snippet wasn't found for code: {}, process: {}", snDef.code, process);
            return EnumsApi.SourceCodeValidateStatus.SNIPPET_NOT_FOUND_ERROR;
        }
        if (!commonProcessValidatorService.checkRequiredVersion(planParamsVersion, snDef)) {
            return EnumsApi.SourceCodeValidateStatus.VERSION_OF_SNIPPET_IS_TOO_LOW_ERROR;
        }
        return OK;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProduceTaskResult {
        public EnumsApi.SourceCodeProducingStatus status;
        public List<String> outputResourceCodes;
        public int numberOfTasks=0;
        public List<Long> taskIds = new ArrayList<>();

        public ProduceTaskResult(EnumsApi.SourceCodeProducingStatus status) {
            this.status = status;
        }
    }

    @Data
    @NoArgsConstructor
    public static class ResourcePools {
        public final Map<String, List<String>> collectedInputs = new HashMap<>();
        public Map<String, SourceCodeParamsYaml.Variable> inputStorageUrls=null;
        public final Map<String, String> mappingCodeToOriginalFilename = new HashMap<>();
        public EnumsApi.SourceCodeProducingStatus status = EnumsApi.SourceCodeProducingStatus.OK;

        public ResourcePools(List<SimpleVariableAndStorageUrl> initialInputResourceCodes) {

            if (initialInputResourceCodes==null || initialInputResourceCodes.isEmpty()) {
                status = EnumsApi.SourceCodeProducingStatus.INPUT_POOL_CODE_DOESNT_EXIST_ERROR;
                return;
            }

            initialInputResourceCodes.forEach(o->
                collectedInputs.computeIfAbsent(o.variable, p -> new ArrayList<>()).add(o.id)
            );

            initialInputResourceCodes.forEach(o-> mappingCodeToOriginalFilename.put(o.id, o.originalFilename));

            inputStorageUrls = initialInputResourceCodes.stream()
                    .collect(Collectors.toMap(o -> o.id, o -> {
                        DataStorageParams p = o.getParams();
                        return new SourceCodeParamsYaml.Variable(p.sourcing, p.git, p.disk, p.storageType);
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
