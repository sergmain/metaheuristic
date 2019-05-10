/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.plan;

import aiai.ai.Consts;
import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.Monitoring;
import aiai.ai.launchpad.beans.*;
import aiai.api.v1.data_storage.DataStorageParams;
import aiai.api.v1.launchpad.Plan;
import aiai.api.v1.launchpad.Process;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.binary_data.SimpleCodeAndStorageUrl;
import aiai.ai.launchpad.atlas.AtlasService;
import aiai.ai.launchpad.experiment.ExperimentProcessService;
import aiai.ai.launchpad.experiment.ExperimentProcessValidator;
import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.file_process.FileProcessService;
import aiai.ai.launchpad.file_process.FileProcessValidator;
import aiai.ai.launchpad.repositories.*;
import aiai.api.v1.data.PlanData;
import aiai.api.v1.data.OperationStatusRest;
import aiai.ai.utils.ControllerUtils;
import aiai.ai.yaml.plan.PlanYaml;
import aiai.ai.yaml.plan.PlanYamlUtils;
import aiai.ai.yaml.input_resource_param.InputResourceParam;
import aiai.ai.yaml.input_resource_param.InputResourceParamUtils;
import aiai.api.v1.EnumsApi;
import aiai.api.v1.launchpad.Task;
import aiai.api.v1.launchpad.Workbook;
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

import static aiai.api.v1.EnumsApi.PlanValidateStatus.PROCESS_VALIDATOR_NOT_FOUND_ERROR;

@Service
@Slf4j
@Profile("launchpad")
public class PlanService {

    private final Globals globals;
    private final PlanYamlUtils planYamlUtils;
    private final ExperimentService experimentService;
    private final BinaryDataService binaryDataService;

    private final ExperimentProcessService experimentProcessService;
    private final FileProcessService fileProcessService;
    private final WorkbookRepository workbookRepository;
    private final TaskRepository taskRepository;
    private final PlanCache planCache;
    private final PlanRepository planRepository;

    private final AtlasService atlasService;
    private final ExperimentRepository experimentRepository;
    private final ExperimentProcessValidator experimentProcessValidator;
    private final FileProcessValidator fileProcessValidator;
    private final ExperimentTaskFeatureRepository taskExperimentFeatureRepository;
    private final WorkbookService workbookService;

    public PlanService(Globals globals, PlanYamlUtils planYamlUtils, ExperimentService experimentService, BinaryDataService binaryDataService, ExperimentProcessService experimentProcessService, FileProcessService fileProcessService, WorkbookRepository workbookRepository, TaskRepository taskRepository, PlanCache planCache, PlanRepository planRepository, AtlasService atlasService, ExperimentRepository experimentRepository, ExperimentProcessValidator experimentProcessValidator, FileProcessValidator fileProcessValidator, ExperimentTaskFeatureRepository taskExperimentFeatureRepository, WorkbookService workbookService) {
        this.globals = globals;
        this.planYamlUtils = planYamlUtils;
        this.experimentService = experimentService;
        this.binaryDataService = binaryDataService;
        this.experimentProcessService = experimentProcessService;
        this.fileProcessService = fileProcessService;
        this.workbookRepository = workbookRepository;
        this.taskRepository = taskRepository;
        this.planCache = planCache;
        this.planRepository = planRepository;
        this.atlasService = atlasService;
        this.experimentRepository = experimentRepository;
        this.experimentProcessValidator = experimentProcessValidator;
        this.fileProcessValidator = fileProcessValidator;
        this.taskExperimentFeatureRepository = taskExperimentFeatureRepository;
        this.workbookService = workbookService;
    }

    // TODO need to check all numbers of errors. Must be as #701.xx

    public Workbook toStarted(Workbook workbook) {
        WorkbookImpl fi = workbookRepository.findById(workbook.getId()).orElse(null);
        if (fi==null) {
            String es = "#701.01 Can't change exec state to PRODUCED for workbook #" + workbook.getId();
            log.error(es);
            throw new IllegalStateException(es);
        }
        PlanImpl plan = planCache.findById(fi.getPlanId());
        if (plan==null) {
            workbook.setExecState(Enums.WorkbookExecState.ERROR.code);
            workbookRepository.save(fi);
            return null;
        }
        fi.setExecState(Enums.WorkbookExecState.STARTED.code);
        return workbookRepository.save(fi);
    }

    // TODO add reporting of producing of tasks
    public synchronized void createAllTasks() {

        Monitoring.log("##019", Enums.Monitor.MEMORY);
        List<WorkbookImpl> workbooks = workbookRepository.findByExecState(
                Enums.WorkbookExecState.PRODUCING.code);
        Monitoring.log("##020", Enums.Monitor.MEMORY);
        if (!workbooks.isEmpty()) {
            log.info("Start producing tasks");
        }
        for (WorkbookImpl workbook : workbooks) {
            PlanImpl plan = planCache.findById(workbook.getPlanId());
            if (plan==null) {
                workbook.setExecState(Enums.WorkbookExecState.ERROR.code);
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
        binaryDataService.deleteByWorkbookId(workbookId);
        List<Workbook> instances = workbookRepository.findByPlanId(planId);
        if (instances.isEmpty()) {
            PlanImpl plan = planRepository.findById(planId).orElse(null);
            if (plan!=null) {
                plan.locked = false;
                save(plan);
            }}
    }

    public PlanData.WorkbookResult getWorkbookExtended(Long workbookId) {
        if (workbookId==null) {
            return new PlanData.WorkbookResult("#560.85 workbookId is null");
        }
        final WorkbookImpl workbook = workbookRepository.findById(workbookId).orElse(null);
        if (workbook == null) {
            return new PlanData.WorkbookResult("#560.87 workbook wasn't found, workbookId: " + workbookId);
        }
        PlanImpl plan = planCache.findById(workbook.getPlanId());
        if (plan == null) {
            return new PlanData.WorkbookResult("#560.89 plan wasn't found, planId: " + workbook.getPlanId());
        }

        if (!plan.getId().equals(workbook.getPlanId())) {
            workbook.setValid(false);
            workbookRepository.save(workbook);
            return new PlanData.WorkbookResult("#560.73 planId doesn't match to workbook.planId, planId: " + workbook.getPlanId()+", workbook.planId: " + workbook.getPlanId());
        }

        PlanData.WorkbookResult result = new PlanData.WorkbookResult(plan, workbook);
        return result;
    }

    public PlanData.PlanValidation validateInternal(Plan plan) {
        final PlanData.PlanValidation planValidation = new PlanData.PlanValidation();
        try {
            planValidation.status = validate(plan);
        } catch (YAMLException e) {
            planValidation.addErrorMessage("#560.34 Error while parsing yaml config, " + e.toString());
            planValidation.status = EnumsApi.PlanValidateStatus.YAML_PARSING_ERROR;
        }
        plan.setValid( planValidation.status == EnumsApi.PlanValidateStatus.OK );
        save(plan);
        if (plan.isValid()) {
            planValidation.infoMessages = Collections.singletonList("Validation result: OK");
        }
        else {
            log.error("Validation error: {}", planValidation.status);
            planValidation.addErrorMessage("#561.01 Validation error: " + planValidation.status);
        }
        return planValidation;
    }

    public OperationStatusRest workbookTargetExecState(Long workbookId, Enums.WorkbookExecState execState) {
        PlanData.WorkbookResult result = getWorkbookExtended(workbookId);
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

        plan.setLocked(true);
        save(plan);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public PlanImpl save(Plan plan) {
        if (plan instanceof PlanImpl) {
            return planCache.save((PlanImpl)plan);
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

    public PlanData.WorkbooksResult getWorkbooksOrderByCreatedOnDescResult(@PathVariable Long id, @PageableDefault(size = 5) Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.workbookRowsLimit, pageable);
        PlanData.WorkbooksResult result = new PlanData.WorkbooksResult();
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

    @Data
    @NoArgsConstructor
    public static class TaskProducingResult {
        public EnumsApi.PlanValidateStatus planValidateStatus = EnumsApi.PlanValidateStatus.NOT_VALIDATED_YET_ERROR;
        public EnumsApi.PlanProducingStatus planProducingStatus = EnumsApi.PlanProducingStatus.NOT_PRODUCING_YET_ERROR;
        public List<Task> tasks = new ArrayList<>();
        public PlanYaml planYaml;
        public Workbook workbook;
        public int numberOfTasks;

        public Enums.TaskProducingStatus getStatus() {
            if (planValidateStatus != EnumsApi.PlanValidateStatus.OK) {
                return Enums.TaskProducingStatus.VERIFY_ERROR;
            }
            if (planProducingStatus!= EnumsApi.PlanProducingStatus.OK) {
                return Enums.TaskProducingStatus.PRODUCING_ERROR;
            }
            return Enums.TaskProducingStatus.OK;
        }
    }

    public TaskProducingResult produceAllTasks(boolean isPersist, PlanImpl plan, Workbook workbook ) {
        TaskProducingResult result = new TaskProducingResult();
        if (isPersist && workbook.getExecState()!=Enums.WorkbookExecState.PRODUCING.code) {
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
        PlanYaml planYaml = planYamlUtils.toPlanYaml(plan.getParams());
        if (planYaml.getProcesses().isEmpty()) {
            return EnumsApi.PlanValidateStatus.NO_ANY_PROCESSES_ERROR;
        }
/*
        // TODO 2019-05-09 decide to delete or don't this check
        if (StringUtils.isBlank(planYaml.getProcesses().get(0).inputType)) {
            return Enums.PlanValidateStatus.INPUT_TYPE_EMPTY_ERROR;
        }
*/

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

    public PlanService.TaskProducingResult createWorkbook(Long planId, String inputResourceParam) {
        PlanService.TaskProducingResult result = new TaskProducingResult();

        InputResourceParam resourceParam = InputResourceParamUtils.to(inputResourceParam);
        List<SimpleCodeAndStorageUrl> inputResourceCodes = binaryDataService.getResourceCodesInPool(resourceParam.getAllPoolCodes());
        if (inputResourceCodes==null || inputResourceCodes.isEmpty()) {
            result.planProducingStatus = EnumsApi.PlanProducingStatus.INPUT_POOL_CODE_DOESNT_EXIST_ERROR;
            return result;
        }

        Workbook fi = new WorkbookImpl();
        fi.setPlanId(planId);
        fi.setCreatedOn(System.currentTimeMillis());
        fi.setExecState(Enums.WorkbookExecState.NONE.code);
        fi.setCompletedOn(null);
        fi.setInputResourceParam(inputResourceParam);
        fi.setProducingOrder(Consts.TASK_ORDER_START_VALUE);
        fi.setValid(true);

        save(fi);
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
        fi.setExecState(Enums.WorkbookExecState.STOPPED.code);
        save(fi);
    }

    public void changeValidStatus(Workbook fi, boolean status) {
        fi.setValid(status);
        save(fi);
    }

    public EnumsApi.PlanProducingStatus toProducing(Workbook fi) {
        fi.setExecState(Enums.WorkbookExecState.PRODUCING.code);
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

            initialInputResourceCodes.forEach(o-> {
                collectedInputs.computeIfAbsent(o.poolCode, p -> new ArrayList<>()).add(o.code);
            });

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

    public TaskProducingResult produceTasks(boolean isPersist, Plan plan, Workbook fi) {

        PlanService.TaskProducingResult result = new PlanService.TaskProducingResult();
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
        result.planYaml = planYamlUtils.toPlanYaml(plan.getParams());

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

    private void toProduced(boolean isPersist, TaskProducingResult result, Workbook fi) {
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
        result.workbook.setExecState(Enums.WorkbookExecState.PRODUCED.code);
        save(result.workbook);
    }

    public void markOrderAsProcessed() {
        List<WorkbookImpl> workbooks = workbookRepository.findByExecState(Enums.WorkbookExecState.STARTED.code);
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
                workbook.setExecState(Enums.WorkbookExecState.FINISHED.code);
                Workbook instance = save(workbook);

                Experiment e = experimentRepository.findByWorkbookId(instance.getId());
                if (e==null) {
                    log.info("#701.23 Can't store an experiment to atlas, the workbook "+instance.getId()+" doesn't contain an experiment" );
                    return instance;
                }
                atlasService.toAtlas(instance.getId(), e.getId());
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
