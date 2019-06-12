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
import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtils;
import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import ai.metaheuristic.api.v1.launchpad.Plan;
import ai.metaheuristic.api.v1.launchpad.Process;
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.binary_data.SimpleCodeAndStorageUrl;
import ai.metaheuristic.ai.launchpad.atlas.AtlasService;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentProcessService;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentProcessValidator;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentService;
import ai.metaheuristic.ai.launchpad.file_process.FileProcessService;
import ai.metaheuristic.ai.launchpad.file_process.FileProcessValidator;
import ai.metaheuristic.api.v1.data.PlanApiData;
import ai.metaheuristic.api.v1.data.OperationStatusRest;
import ai.metaheuristic.ai.launchpad.repositories.*;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.v1.data.InputResourceParam;
import ai.metaheuristic.ai.yaml.input_resource_param.InputResourceParamUtils;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.launchpad.Task;
import ai.metaheuristic.api.v1.launchpad.Workbook;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.*;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.v1.EnumsApi.PlanValidateStatus.PROCESS_VALIDATOR_NOT_FOUND_ERROR;

@Service
@Slf4j
@Profile("launchpad")
public class PlanService {

    private final Globals globals;
    private final ExperimentService experimentService;
    private final BinaryDataService binaryDataService;

    private final ExperimentProcessService experimentProcessService;
    private final FileProcessService fileProcessService;
    private final WorkbookRepository workbookRepository;
    private final TaskRepository taskRepository;
    private final PlanCache planCache;

    private final AtlasService atlasService;
    private final ExperimentRepository experimentRepository;
    private final ExperimentProcessValidator experimentProcessValidator;
    private final FileProcessValidator fileProcessValidator;
    private final ExperimentTaskFeatureRepository taskExperimentFeatureRepository;
    private final WorkbookService workbookService;

    public PlanService(Globals globals, ExperimentService experimentService, BinaryDataService binaryDataService, ExperimentProcessService experimentProcessService, FileProcessService fileProcessService, WorkbookRepository workbookRepository, TaskRepository taskRepository, PlanCache planCache, AtlasService atlasService, ExperimentRepository experimentRepository, ExperimentProcessValidator experimentProcessValidator, FileProcessValidator fileProcessValidator, ExperimentTaskFeatureRepository taskExperimentFeatureRepository, WorkbookService workbookService) {
        this.globals = globals;
        this.experimentService = experimentService;
        this.binaryDataService = binaryDataService;
        this.experimentProcessService = experimentProcessService;
        this.fileProcessService = fileProcessService;
        this.workbookRepository = workbookRepository;
        this.taskRepository = taskRepository;
        this.planCache = planCache;
        this.atlasService = atlasService;
        this.experimentRepository = experimentRepository;
        this.experimentProcessValidator = experimentProcessValidator;
        this.fileProcessValidator = fileProcessValidator;
        this.taskExperimentFeatureRepository = taskExperimentFeatureRepository;
        this.workbookService = workbookService;
    }

    // TODO 2019.05.25 need to check all numbers of errors. Must be as #701.xx

    public Workbook toStarted(Workbook workbook) {
        WorkbookImpl fi = workbookRepository.findById(workbook.getId()).orElse(null);
        if (fi==null) {
            String es = "#701.01 Can't change exec state to PRODUCED for workbook #" + workbook.getId();
            log.error(es);
            throw new IllegalStateException(es);
        }
        PlanImpl plan = planCache.findById(fi.getPlanId());
        if (plan==null) {
            workbook.setExecState(EnumsApi.WorkbookExecState.ERROR.code);
            workbookRepository.save(fi);
            return null;
        }
        fi.setExecState(EnumsApi.WorkbookExecState.STARTED.code);
        return workbookRepository.save(fi);
    }

    // TODO 2019.05.19 add reporting of producing of tasks
    public synchronized void createAllTasks() {

        Monitoring.log("##019", Enums.Monitor.MEMORY);
        List<WorkbookImpl> workbooks = workbookRepository.findByExecState(EnumsApi.WorkbookExecState.PRODUCING.code);
        Monitoring.log("##020", Enums.Monitor.MEMORY);
        if (!workbooks.isEmpty()) {
            log.info("Start producing tasks");
        }
        for (WorkbookImpl workbook : workbooks) {
            PlanImpl plan = planCache.findById(workbook.getPlanId());
            if (plan==null) {
                workbook.setExecState(EnumsApi.WorkbookExecState.ERROR.code);
                workbookRepository.save(workbook);
                continue;
            }
            Monitoring.log("##021", Enums.Monitor.MEMORY);
            log.info("Producing tasks for plan.code: {}, input resource pool: \n{}",plan.code, workbook.inputResourceParam);
            produceAllTasks(true, plan, workbook);
            Monitoring.log("##022", Enums.Monitor.MEMORY);
        }
        if (!workbooks.isEmpty()) {
            log.info("Producing of tasks was finished");
        }
    }

    public void deleteWorkbook(Long workbookId, long planId) {
        experimentService.resetExperiment(workbookId);
        workbookService.deleteById(workbookId);
        taskExperimentFeatureRepository.deleteByWorkbookId(workbookId);
        binaryDataService.deleteByRefId(workbookId, EnumsApi.BinaryDataRefType.workbook);
        Workbook workbook = workbookRepository.findFirstByPlanId(planId);
        if (workbook==null) {
            Plan p = planCache.findById(planId);
            if (p!=null) {
                setLockedTo(p, false);
            }
        }
    }

    public PlanApiData.WorkbookResult getWorkbookExtended(Long workbookId) {
        if (workbookId==null) {
            return new PlanApiData.WorkbookResult("#560.85 workbookId is null");
        }
        final WorkbookImpl workbook = workbookRepository.findById(workbookId).orElse(null);
        if (workbook == null) {
            return new PlanApiData.WorkbookResult("#560.87 workbook wasn't found, workbookId: " + workbookId);
        }
        PlanImpl plan = planCache.findById(workbook.getPlanId());
        if (plan == null) {
            return new PlanApiData.WorkbookResult("#560.89 plan wasn't found, planId: " + workbook.getPlanId());
        }

        if (!plan.getId().equals(workbook.getPlanId())) {
            workbook.setValid(false);
            workbookRepository.save(workbook);
            return new PlanApiData.WorkbookResult("#560.73 planId doesn't match to workbook.planId, planId: " + workbook.getPlanId()+", workbook.planId: " + workbook.getPlanId());
        }

        //noinspection UnnecessaryLocalVariable
        PlanApiData.WorkbookResult result = new PlanApiData.WorkbookResult(plan, workbook);
        return result;
    }

    public PlanApiData.PlanValidation validateInternal(Plan plan) {
        final PlanApiData.PlanValidation planValidation = new PlanApiData.PlanValidation();
        try {
            planValidation.status = validate(plan);
        } catch (YAMLException e) {
            planValidation.addErrorMessage("#560.34 Error while parsing yaml config, " + e.toString());
            planValidation.status = EnumsApi.PlanValidateStatus.YAML_PARSING_ERROR;
        }
        setValidTo(plan, planValidation.status == EnumsApi.PlanValidateStatus.OK );
        if (plan.isValid()) {
            planValidation.infoMessages = Collections.singletonList("Validation result: OK");
        }
        else {
            final String es = "#561.01 Validation error: " + planValidation.status;
            log.error(es);
            planValidation.addErrorMessage(es);
        }
        return planValidation;
    }

    public OperationStatusRest workbookTargetExecState(Long workbookId, EnumsApi.WorkbookExecState execState) {
        PlanApiData.WorkbookResult result = getWorkbookExtended(workbookId);
        if (result.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, result.errorMessages);
        }

        final Workbook workbook = result.workbook;
        final Plan plan = result.plan;
        if (plan ==null || workbook ==null) {
            throw new IllegalStateException("Error: (result.plan==null || result.workbook==null)");
        }

        workbook.setExecState(execState.code);
        save(workbook);

        setLockedTo(plan, true);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    private final static Object syncObj = new Object();

    // TODO 2019.05.25 not so good but just workaround for ObjectOptimisticLockingFailureException
    private void setValidTo(Plan plan, boolean valid) {
        synchronized (syncObj) {
            Plan p = planCache.findById(plan.getId());
            if (p!=null && p.isValid()!=valid) {
                p.setValid(true);
                saveInternal(p);
            }
        }
    }

    private void setLockedTo(Plan plan, boolean locked) {
        synchronized (syncObj) {
            Plan p = planCache.findById(plan.getId());
            if (p!=null && p.isLocked()!=locked) {
                p.setLocked(locked);
                saveInternal(p);
            }
        }
    }

    private void saveInternal(Plan plan) {
        if (plan instanceof PlanImpl) {
            planCache.save((PlanImpl)plan);
        }
        else {
            throw new NotImplementedException("Need to implement");
        }
    }

    public Workbook save(Workbook workbook) {
        if (workbook instanceof WorkbookImpl) {
            return workbookRepository.save((WorkbookImpl)workbook);
        }
        else {
            throw new NotImplementedException("Need to implement");
        }
    }

    public PlanApiData.WorkbooksResult getWorkbooksOrderByCreatedOnDescResult(@PathVariable Long id, @PageableDefault(size = 5) Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.workbookRowsLimit, pageable);
        PlanApiData.WorkbooksResult result = new PlanApiData.WorkbooksResult();
        result.instances = workbookRepository.findByPlanIdOrderByCreatedOnDesc(pageable, id);
        result.currentPlanId = id;

        for (Workbook workbook : result.instances) {
            Plan plan = planCache.findById(workbook.getPlanId());
            if (plan==null) {
                log.warn("#560.51 Found workbook with wrong planId. planId: {}", workbook.getPlanId());
                continue;
            }
            result.plans.put(workbook.getId(), plan);
        }
        return result;
    }

    public PlanApiData.TaskProducingResultComplex produceAllTasks(boolean isPersist, PlanImpl plan, Workbook workbook ) {
        PlanApiData.TaskProducingResultComplex result = new PlanApiData.TaskProducingResultComplex();
        if (isPersist && workbook.getExecState()!= EnumsApi.WorkbookExecState.PRODUCING.code) {
            result.planValidateStatus = EnumsApi.PlanValidateStatus.ALREADY_PRODUCED_ERROR;
            return result;
        }
        long mills = System.currentTimeMillis();
        result.planValidateStatus = validate(plan);
        log.info("Plan was validated for "+(System.currentTimeMillis() - mills) + " ms.");
        if (result.planValidateStatus != EnumsApi.PlanValidateStatus.OK &&
                result.planValidateStatus != EnumsApi.PlanValidateStatus.EXPERIMENT_ALREADY_STARTED_ERROR ) {
            log.error("#701.07 Can't produce tasks, error: {}", result.planValidateStatus);
            toStopped(isPersist, workbook.getId());
            return result;
        }
        Monitoring.log("##022", Enums.Monitor.MEMORY);
        mills = System.currentTimeMillis();
        result = produceTasks(isPersist, plan, workbook);
        log.info("PlanService.produceTasks() was processed for "+(System.currentTimeMillis() - mills) + " ms.");
        Monitoring.log("##033", Enums.Monitor.MEMORY);

        return result;
    }

    public EnumsApi.PlanValidateStatus validate(Plan plan) {
        if (plan==null) {
            return EnumsApi.PlanValidateStatus.NO_ANY_PROCESSES_ERROR;
        }
        if (StringUtils.isBlank(plan.getCode())) {
            return EnumsApi.PlanValidateStatus.PLAN_CODE_EMPTY_ERROR;
        }
        if (StringUtils.isBlank(plan.getParams())) {
            return EnumsApi.PlanValidateStatus.PLAN_PARAMS_EMPTY_ERROR;
        }
        PlanApiData.PlanParamsYaml planParams = PlanParamsYamlUtils.to(plan.getParams());
        PlanApiData.PlanYaml planYaml = planParams.planYaml;
        if (planYaml.getProcesses().isEmpty()) {
            return EnumsApi.PlanValidateStatus.NO_ANY_PROCESSES_ERROR;
        }

        Process lastProcess = null;
        boolean experimentPresent = false;
        List<Process> processes = planYaml.getProcesses();
        for (int i = 0; i < processes.size(); i++) {
            Process process = processes.get(i);
            if (i + 1 < processes.size()) {
                if (process.outputParams == null) {
                    return EnumsApi.PlanValidateStatus.PROCESS_PARAMS_EMPTY_ERROR;
                }
                if (StringUtils.isBlank(process.outputParams.storageType)) {
                    return EnumsApi.PlanValidateStatus.OUTPUT_TYPE_EMPTY_ERROR;
                }
            }
            lastProcess = process;
            if (StringUtils.containsWhitespace(process.code) || StringUtils.contains(process.code, ',') ){
                return EnumsApi.PlanValidateStatus.PROCESS_CODE_CONTAINS_ILLEGAL_CHAR_ERROR;
            }
            if (StringUtils.containsWhitespace(process.inputResourceCode) || StringUtils.contains(process.inputResourceCode, ',') ){
                return EnumsApi.PlanValidateStatus.RESOURCE_CODE_CONTAINS_ILLEGAL_CHAR_ERROR;
            }
            if (StringUtils.containsWhitespace(process.outputResourceCode) || StringUtils.contains(process.outputResourceCode, ',') ){
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
            EnumsApi.PlanValidateStatus status = processValidator.validate(plan, process, i==0);
            if (status!=null) {
                return status;
            }

            if (process.parallelExec && (process.snippetCodes==null || process.snippetCodes.size()<2)) {
                return EnumsApi.PlanValidateStatus.NOT_ENOUGH_FOR_PARALLEL_EXEC_ERROR;
            }
        }
        if (experimentPresent && lastProcess.type!= EnumsApi.ProcessType.EXPERIMENT) {
            return  EnumsApi.PlanValidateStatus.EXPERIMENT_MUST_BE_LAST_PROCESS_ERROR;
        }
        return EnumsApi.PlanValidateStatus.OK;
    }

    @Data
    public static class ProduceTaskResult {
        public EnumsApi.PlanProducingStatus status;
        public List<String> outputResourceCodes;
        public int numberOfTasks;
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

        save(fi);
        result.planProducingStatus = EnumsApi.PlanProducingStatus.OK;
        result.workbook = fi;

        return result;
    }

    private void toStopped(boolean isPersist, long workbookId) {
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

    @Data
    @NoArgsConstructor
    public static class ResourcePools {
        public final Map<String, List<String>> collectedInputs = new HashMap<>();
        public Map<String, DataStorageParams> inputStorageUrls=null;
        public EnumsApi.PlanProducingStatus status = EnumsApi.PlanProducingStatus.OK;

        public ResourcePools(List<SimpleCodeAndStorageUrl> initialInputResourceCodes) {

            if (initialInputResourceCodes==null || initialInputResourceCodes.isEmpty()) {
                status = EnumsApi.PlanProducingStatus.INPUT_POOL_CODE_DOESNT_EXIST_ERROR;
                return;
            }

            initialInputResourceCodes.forEach(o->
                collectedInputs.computeIfAbsent(o.poolCode, p -> new ArrayList<>()).add(o.code)
            );

            //noinspection Convert2MethodRef
            inputStorageUrls = initialInputResourceCodes
                    .stream()
                    .collect(Collectors.toMap(o -> o.code, o -> o.getParams()));

        }

        public void clean() {
            collectedInputs.values().forEach(o-> o.forEach(inputStorageUrls::remove));
            collectedInputs.clear();
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
        }
    }

    public PlanApiData.TaskProducingResultComplex produceTasks(boolean isPersist, Plan plan, Workbook fi) {

        PlanApiData.TaskProducingResultComplex result = new PlanApiData.TaskProducingResultComplex();
        result.planValidateStatus = EnumsApi.PlanValidateStatus.OK;

        Monitoring.log("##023", Enums.Monitor.MEMORY);
        long mill = System.currentTimeMillis();
        InputResourceParam resourceParams = InputResourceParamUtils.to(fi.getInputResourceParam());
        List<SimpleCodeAndStorageUrl> initialInputResourceCodes;
        initialInputResourceCodes = binaryDataService.getResourceCodesInPool(resourceParams.getAllPoolCodes());
        log.info("Resources was acquired for " + (System.currentTimeMillis() - mill) +" ms" );

        ResourcePools pools = new ResourcePools(initialInputResourceCodes);
        if (pools.status!= EnumsApi.PlanProducingStatus.OK) {
            result.planProducingStatus = pools.status;
            return result;
        }
        if (resourceParams.preservePoolNames) {
            final Map<String, List<String>> collectedInputs = new HashMap<>();
            pools.collectedInputs.forEach( (key, value) -> {
                String newKey = null;
                for (Map.Entry<String, List<String>> entry : resourceParams.poolCodes.entrySet()) {
                    if (entry.getValue().contains(key)) {
                        newKey = entry.getKey();
                        break;
                    }
                }
                if (newKey==null) {
                    log.error("#701.09 Can't find key for pool code {}", key );
                    result.planProducingStatus = EnumsApi.PlanProducingStatus.ERROR;
                    return;
                }
                collectedInputs.put(newKey, value);
            });
            pools.collectedInputs.clear();
            pools.collectedInputs.putAll(collectedInputs);
        }

        Monitoring.log("##025", Enums.Monitor.MEMORY);

        result.workbook = fi;
        PlanApiData.PlanParamsYaml planParams = PlanParamsYamlUtils.to(plan.getParams());
        result.planYaml = planParams.planYaml;

        plan.setClean( result.planYaml.clean );
        int idx = Consts.TASK_ORDER_START_VALUE;
        result.planProducingStatus = EnumsApi.PlanProducingStatus.OK;
        for (Process process : result.planYaml.getProcesses()) {
            process.order = idx++;

            ProduceTaskResult produceTaskResult;
            switch(process.type) {
                case FILE_PROCESSING:
                    Monitoring.log("##026", Enums.Monitor.MEMORY);
                    produceTaskResult = fileProcessService.produceTasks(isPersist, plan, fi, process, pools);
                    Monitoring.log("##027", Enums.Monitor.MEMORY);
                    break;
                case EXPERIMENT:
                    Monitoring.log("##028", Enums.Monitor.MEMORY);
                    produceTaskResult = experimentProcessService.produceTasks(isPersist, plan, fi, process, pools);
                    Monitoring.log("##029", Enums.Monitor.MEMORY);
                    break;
                default:
                    throw new IllegalStateException("#701.11 Unknown process type");
            }
            result.numberOfTasks += produceTaskResult.numberOfTasks;
            if (produceTaskResult.status != EnumsApi.PlanProducingStatus.OK) {
                result.planProducingStatus = produceTaskResult.status;
                return result;
            }
            if (!process.collectResources) {
                pools.clean();
            }
            Monitoring.log("##030", Enums.Monitor.MEMORY);
            pools.add(process.outputParams.storageType, produceTaskResult.outputResourceCodes);
            Monitoring.log("##031", Enums.Monitor.MEMORY);
        }
        toProduced(isPersist, result, fi);

        result.planProducingStatus = EnumsApi.PlanProducingStatus.OK;
        return result;
    }

    private void toProduced(boolean isPersist, PlanApiData.TaskProducingResultComplex result, Workbook fi) {
        if (!isPersist) {
            return;
        }
        Long id = fi.getId();
        result.workbook = workbookRepository.findById(id).orElse(null);
        if (result.workbook==null) {
            String es = "#701.16 Can't change exec state to PRODUCED for workbook #" + id;
            log.error(es);
            throw new IllegalStateException(es);
        }
        result.workbook.setExecState(EnumsApi.WorkbookExecState.PRODUCED.code);
        save(result.workbook);
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
                throw new IllegalStateException("#701.21 count of records is null");
            }
            if (count==0) {
                log.info("Workbook #{} was finished", workbook.getId());
                experimentService.updateMaxValueForExperimentFeatures(workbook.getId());
                workbook.setCompletedOn(System.currentTimeMillis());
                workbook.setExecState(EnumsApi.WorkbookExecState.FINISHED.code);
                Workbook instance = save(workbook);

                Long experimentId = experimentRepository.findIdByWorkbookId(instance.getId());
                if (experimentId==null) {
                    log.info("#701.23 Can't store an experiment to atlas, the workbook "+instance.getId()+" doesn't contain an experiment" );
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

    public static String asInputResourceParams(String poolCode) {
        return "poolCodes:\n  "+Consts.WORKBOOK_INPUT_TYPE+":\n" +
                "  - " + poolCode;
    }
}
