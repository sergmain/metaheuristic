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
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.Monitoring;
import ai.metaheuristic.ai.exceptions.BreakFromForEachException;
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.binary_data.SimpleCodeAndStorageUrl;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentProcessService;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentProcessValidator;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentService;
import ai.metaheuristic.ai.launchpad.file_process.FileProcessService;
import ai.metaheuristic.ai.launchpad.file_process.FileProcessValidator;
import ai.metaheuristic.ai.launchpad.repositories.PlanRepository;
import ai.metaheuristic.ai.launchpad.repositories.SnippetRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookCache;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.YamlVersion;
import ai.metaheuristic.api.data.plan.PlanApiData;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.api.launchpad.Plan;
import ai.metaheuristic.api.launchpad.Workbook;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.yaml.versioning.YamlForVersioning;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.*;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.EnumsApi.PlanValidateStatus.OK;
import static ai.metaheuristic.api.EnumsApi.PlanValidateStatus.PROCESS_VALIDATOR_NOT_FOUND_ERROR;

@Service
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class PlanService {

    private final Globals globals;
    private final BinaryDataService binaryDataService;

    private final ExperimentProcessService experimentProcessService;
    private final ExperimentService experimentService;
    private final ExperimentProcessValidator experimentProcessValidator;

    private final FileProcessService fileProcessService;
    private final WorkbookRepository workbookRepository;
    private final PlanCache planCache;
    private final PlanRepository planRepository;

    private final FileProcessValidator fileProcessValidator;
    private final WorkbookService workbookService;
    private final WorkbookCache workbookCache;
    private final CommonProcessValidatorService commonProcessValidatorService;
    private final SnippetRepository snippetRepository;

    public void toStarted(Workbook workbook) {
        PlanImpl plan = planCache.findById(workbook.getPlanId());
        if (plan == null) {
            workbookService.toError(workbook.getId());
        }
        else {
            workbookService.toStarted(workbook.getId());
        }
    }

    // TODO 2019.05.19 add reporting of producing of tasks
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
                workbookService.toStopped(workbook.id);
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

    public PlanApiData.WorkbookResult getAddWorkbookInternal(String poolCode, String inputResourceParams, @NonNull PlanImpl plan) {
        if (StringUtils.isBlank(poolCode) && StringUtils.isBlank(inputResourceParams)) {
            return new PlanApiData.WorkbookResult("#560.063 both inputResourcePoolCode of Workbook and inputResourceParams are empty");
        }

        if (StringUtils.isNotBlank(poolCode) && StringUtils.isNotBlank(inputResourceParams)) {
            return new PlanApiData.WorkbookResult("#560.065 both inputResourcePoolCode of Workbook and inputResourceParams aren't empty");
        }

        // validate the plan
        PlanApiData.PlanValidation planValidation = validateInternal(plan);
        if (planValidation.status != EnumsApi.PlanValidateStatus.OK) {
            PlanApiData.WorkbookResult r = new PlanApiData.WorkbookResult();
            r.errorMessages = planValidation.errorMessages;
            return r;
        }

        WorkbookParamsYaml.WorkbookResourceCodes wrc = PlanUtils.prepareResourceCodes(poolCode, inputResourceParams);
        PlanApiData.TaskProducingResultComplex producingResult = workbookService.createWorkbook(plan.getId(), wrc);
        if (producingResult.planProducingStatus != EnumsApi.PlanProducingStatus.OK) {
            return new PlanApiData.WorkbookResult("#560.072 Error creating workbook: " + producingResult.planProducingStatus);
        }

        PlanApiData.TaskProducingResultComplex countTasks = produceTasks(false, plan, producingResult.workbook.getId());
        if (countTasks.planProducingStatus != EnumsApi.PlanProducingStatus.OK) {
            workbookService.changeValidStatus(producingResult.workbook.getId(), false);
            return new PlanApiData.WorkbookResult("#560.077 plan producing was failed, status: " + countTasks.planProducingStatus);
        }

        if (globals.maxTasksPerWorkbook < countTasks.numberOfTasks) {
            workbookService.changeValidStatus(producingResult.workbook.getId(), false);
            return new PlanApiData.WorkbookResult("#560.081 number of tasks for this workbook exceeded the allowed maximum number. Workbook was created but its status is 'not valid'. " +
                    "Allowed maximum number of tasks: " + globals.maxTasksPerWorkbook + ", tasks in this workbook:  " + countTasks.numberOfTasks);
        }

        PlanApiData.WorkbookResult result = new PlanApiData.WorkbookResult(plan);
        result.workbook = producingResult.workbook;

        workbookService.changeValidStatus(producingResult.workbook.getId(), true);

        return result;
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

    public OperationStatusRest workbookTargetExecState(Long workbookId, EnumsApi.WorkbookExecState execState) {
        PlanApiData.WorkbookResult result = workbookService.getWorkbookExtended(workbookId);
        if (result.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, result.errorMessages);
        }

        final WorkbookImpl workbook = (WorkbookImpl) result.workbook;
        final Plan plan = result.plan;
        if (plan==null || workbook ==null) {
            throw new IllegalStateException("#701.110 Error: (result.plan==null || result.workbook==null)");
        }

        if (workbook.execState!=execState.code) {
            workbookService.toState(workbook.id, execState);
            setLockedTo(plan.getId(), plan.getCompanyId(), true);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
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
                workbookService.toStopped(workbook.getId());
            }
            return result;
        }
        Monitoring.log("##022", Enums.Monitor.MEMORY);
        mills = System.currentTimeMillis();
        result = produceTasks(isPersist, plan, workbook.getId());
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
        PlanParamsYaml.PlanYaml planYaml = planParams.planYaml;
        if (planYaml.getProcesses().isEmpty()) {
            return EnumsApi.PlanValidateStatus.NO_ANY_PROCESSES_ERROR;
        }

        PlanParamsYaml.Process lastProcess = null;
        boolean experimentPresent = false;
        List<PlanParamsYaml.Process> processes = planYaml.getProcesses();
        for (int i = 0; i < processes.size(); i++) {
            PlanParamsYaml.Process process = processes.get(i);
            if (i + 1 < processes.size()) {
                if (process.outputParams == null) {
                    return EnumsApi.PlanValidateStatus.PROCESS_PARAMS_EMPTY_ERROR;
                }
                if (StringUtils.isBlank(process.outputParams.storageType)) {
                    return EnumsApi.PlanValidateStatus.OUTPUT_TYPE_EMPTY_ERROR;
                }
            }
            lastProcess = process;
            if (S.b(process.code) || !StrUtils.isCodeOk(process.code)){
                log.error("Error while validating plan {}", planYaml);
                return EnumsApi.PlanValidateStatus.PROCESS_CODE_CONTAINS_ILLEGAL_CHAR_ERROR;
            }
            if (process.inputResourceCode!=null && !StrUtils.isCodeOk(process.inputResourceCode)){
                return EnumsApi.PlanValidateStatus.RESOURCE_CODE_CONTAINS_ILLEGAL_CHAR_ERROR;
            }
            if (process.outputResourceCode!=null && !StrUtils.isCodeOk(process.outputResourceCode)){
                return EnumsApi.PlanValidateStatus.RESOURCE_CODE_CONTAINS_ILLEGAL_CHAR_ERROR;
            }
            ProcessValidator processValidator;
            if (process.type == EnumsApi.ProcessType.EXPERIMENT) {
                experimentPresent = true;
                processValidator = experimentProcessValidator;
            }
            else if (process.type == EnumsApi.ProcessType.FILE_PROCESSING) {
                processValidator = fileProcessValidator;
            }
            else {
                return PROCESS_VALIDATOR_NOT_FOUND_ERROR;
            }
            EnumsApi.PlanValidateStatus status;
            status = checkSnippets(plan, process);
            if (status!=OK) {
                return status;
            }
            status = processValidator.validate(plan, process, i==0);
            if (status!=null) {
                return status;
            }

            if (process.parallelExec && (process.snippets==null || process.snippets.size()<2)) {
                return EnumsApi.PlanValidateStatus.NOT_ENOUGH_FOR_PARALLEL_EXEC_ERROR;
            }
        }
        if (experimentPresent && lastProcess.type!= EnumsApi.ProcessType.EXPERIMENT) {
            return  EnumsApi.PlanValidateStatus.EXPERIMENT_MUST_BE_LAST_PROCESS_ERROR;
        }
        return EnumsApi.PlanValidateStatus.OK;
    }

    private EnumsApi.PlanValidateStatus checkSnippets(Plan plan, PlanParamsYaml.Process process) {
        YamlVersion v = YamlForVersioning.getYamlForVersion().load(plan.getParams());

        if (process.snippets!=null) {
            for (PlanParamsYaml.SnippetDefForPlan snDef : process.snippets) {
                EnumsApi.PlanValidateStatus x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x != OK) {
                    log.error("#177.030 Pre-snippet wasn't found for code: {}, process: {}", snDef.code, process);
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
    public static class ProduceTaskResult {
        public EnumsApi.PlanProducingStatus status;
        public List<String> outputResourceCodes;
        public int numberOfTasks;
        public List<Long> taskIds = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class ResourcePools {
        public final Map<String, List<String>> collectedInputs = new HashMap<>();
        public Map<String, DataStorageParams> inputStorageUrls=null;
        public final Map<String, String> mappingCodeToOriginalFilename = new HashMap<>();
        public EnumsApi.PlanProducingStatus status = EnumsApi.PlanProducingStatus.OK;

        public ResourcePools(List<SimpleCodeAndStorageUrl> initialInputResourceCodes) {

            if (initialInputResourceCodes==null || initialInputResourceCodes.isEmpty()) {
                status = EnumsApi.PlanProducingStatus.INPUT_POOL_CODE_DOESNT_EXIST_ERROR;
                return;
            }

            initialInputResourceCodes.forEach(o->
                collectedInputs.computeIfAbsent(o.poolCode, p -> new ArrayList<>()).add(o.code)
            );

            initialInputResourceCodes.forEach(o-> mappingCodeToOriginalFilename.put(o.code, o.originalFilename));

            //noinspection Convert2MethodRef
            inputStorageUrls = initialInputResourceCodes
                    .stream()
                    .collect(Collectors.toMap(o -> o.code, o -> o.getParams()));

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

    public PlanApiData.TaskProducingResultComplex produceTasks(boolean isPersist, PlanImpl plan, Long workbookId) {

        Monitoring.log("##023", Enums.Monitor.MEMORY);
        long mill = System.currentTimeMillis();

        WorkbookParamsYaml resourceParams;
        {
            WorkbookImpl workbook = workbookCache.findById(workbookId);
            if (workbook == null) {
                log.error("#701.175 Can't find workbook #{}", workbookId);
                return new PlanApiData.TaskProducingResultComplex(EnumsApi.PlanValidateStatus.WORKBOOK_NOT_FOUND_ERROR);
            }

            resourceParams = workbook.getWorkbookParamsYaml();
        }
        List<SimpleCodeAndStorageUrl> initialInputResourceCodes;
        initialInputResourceCodes = binaryDataService.getResourceCodesInPool(resourceParams.getAllPoolCodes());
        log.info("#701.180 Resources was acquired for " + (System.currentTimeMillis() - mill) +" ms" );

        ResourcePools pools = new ResourcePools(initialInputResourceCodes);
        if (pools.status!= EnumsApi.PlanProducingStatus.OK) {
            return new PlanApiData.TaskProducingResultComplex(pools.status);
        }

        if (resourceParams.workbookYaml.preservePoolNames) {
            final Map<String, List<String>> collectedInputs = new HashMap<>();
            try {
                pools.collectedInputs.forEach( (key, value) -> {
                    String newKey = null;
                    for (Map.Entry<String, List<String>> entry : resourceParams.workbookYaml.poolCodes.entrySet()) {
                        if (entry.getValue().contains(key)) {
                            newKey = entry.getKey();
                            break;
                        }
                    }
                    if (newKey==null) {
                        log.error("#701.190 Can't find key for pool code {}", key );
                        throw new BreakFromForEachException();
                    }
                    collectedInputs.put(newKey, value);
                });
            } catch (BreakFromForEachException e) {
                return new PlanApiData.TaskProducingResultComplex(EnumsApi.PlanProducingStatus.ERROR);
            }

            pools.collectedInputs.clear();
            pools.collectedInputs.putAll(collectedInputs);
        }

        Monitoring.log("##025", Enums.Monitor.MEMORY);
        PlanParamsYaml planParams = plan.getPlanParamsYaml();

        int idx = Consts.PROCESS_ORDER_START_VALUE;
        List<Long> parentTaskIds = new ArrayList<>();
        int numberOfTasks=0;
        for (PlanParamsYaml.Process process : planParams.planYaml.getProcesses()) {
            process.order = idx++;

            ProduceTaskResult produceTaskResult;
            switch(process.type) {
                case FILE_PROCESSING:
                    Monitoring.log("##026", Enums.Monitor.MEMORY);
                    produceTaskResult = fileProcessService.produceTasks(isPersist, plan.getId(), planParams, workbookId, process, pools, parentTaskIds);
                    Monitoring.log("##027", Enums.Monitor.MEMORY);
                    break;
                case EXPERIMENT:
                    Monitoring.log("##028", Enums.Monitor.MEMORY);
                    produceTaskResult = experimentProcessService.produceTasks(isPersist, planParams, workbookId, process, pools, parentTaskIds);
                    Monitoring.log("##029", Enums.Monitor.MEMORY);
                    break;
                default:
                    throw new IllegalStateException("#701.200 Unknown process type");
            }
            parentTaskIds.clear();
            parentTaskIds.addAll(produceTaskResult.taskIds);

            numberOfTasks += produceTaskResult.numberOfTasks;
            if (produceTaskResult.status != EnumsApi.PlanProducingStatus.OK) {
                return new PlanApiData.TaskProducingResultComplex(produceTaskResult.status);
            }
            if (!process.collectResources) {
                pools.clean();
            }
            Monitoring.log("##030", Enums.Monitor.MEMORY);
            if (process.outputParams.storageType!=null) {
                pools.add(process.outputParams.storageType, produceTaskResult.outputResourceCodes);
                for (String outputResourceCode : produceTaskResult.outputResourceCodes) {
                    pools.inputStorageUrls.put(outputResourceCode, process.outputParams);
                }
            }
            Monitoring.log("##031", Enums.Monitor.MEMORY);
        }

        PlanApiData.TaskProducingResultComplex result = new PlanApiData.TaskProducingResultComplex();
        if (isPersist) {
            workbookService.toProduced(workbookId);
        }
        result.workbook = workbookCache.findById(workbookId);
        result.planYaml = planParams.planYaml;
        result.numberOfTasks += numberOfTasks;
        result.planValidateStatus = EnumsApi.PlanValidateStatus.OK;
        result.planProducingStatus = EnumsApi.PlanProducingStatus.OK;

        return result;
    }

    // ========= Workbook specific =============

    public void deleteWorkbook(Long workbookId, Long companyUniqueId) {
        experimentService.resetExperimentByWorkbookId(workbookId);
        binaryDataService.deleteByRefId(workbookId, EnumsApi.BinaryDataRefType.workbook);
        Workbook workbook = workbookCache.findById(workbookId);
        if (workbook != null) {
            // unlock plan if this is the last workbook in the plan
            List<Long> ids = workbookRepository.findIdsByPlanId(workbook.getPlanId());
            if (ids.size()==1) {
                if (ids.get(0).equals(workbookId)) {
                    if (workbook.getPlanId() != null) {
                        setLockedTo(workbook.getPlanId(), companyUniqueId, false);
                    }
                }
                else {
                    log.warn("#701.300 unexpected state, workbookId: {}, ids: {}, ", workbookId, ids);
                }
            }
            else if (ids.isEmpty()) {
                log.warn("#701.310 unexpected state, workbookId: {}, ids is empty", workbookId);
            }
            workbookCache.deleteById(workbookId);
        }
    }


}
