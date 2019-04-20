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

package aiai.ai.launchpad.flow;

import aiai.ai.Consts;
import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.Monitoring;
import aiai.ai.launchpad.beans.*;
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
import aiai.ai.launchpad.data.FlowData;
import aiai.ai.launchpad.data.OperationStatusRest;
import aiai.ai.utils.ControllerUtils;
import aiai.ai.yaml.flow.FlowYaml;
import aiai.ai.yaml.flow.FlowYamlUtils;
import aiai.ai.yaml.input_resource_param.InputResourceParam;
import aiai.ai.yaml.input_resource_param.InputResourceParamUtils;
import aiai.api.v1.EnumsApi;
import aiai.api.v1.launchpad.Task;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.*;
import java.util.stream.Collectors;

import static aiai.ai.Enums.FlowValidateStatus.PROCESS_VALIDATOR_NOT_FOUND_ERROR;

@Service
@Slf4j
@Profile("launchpad")
public class FlowService {

    private final Globals globals;
    private final FlowYamlUtils flowYamlUtils;
    private final ExperimentService experimentService;
    private final BinaryDataService binaryDataService;

    private final ExperimentProcessService experimentProcessService;
    private final FileProcessService fileProcessService;
    private final FlowInstanceRepository flowInstanceRepository;
    private final TaskRepository taskRepository;
    private final FlowCache flowCache;
    private final FlowRepository flowRepository;

    private final AtlasService atlasService;
    private final ExperimentRepository experimentRepository;
    private final ExperimentProcessValidator experimentProcessValidator;
    private final FileProcessValidator fileProcessValidator;
    private final ExperimentTaskFeatureRepository taskExperimentFeatureRepository;
    private final FlowInstanceService flowInstanceService;

    public FlowService(Globals globals, FlowYamlUtils flowYamlUtils, ExperimentService experimentService, BinaryDataService binaryDataService, ExperimentProcessService experimentProcessService, FileProcessService fileProcessService, FlowInstanceRepository flowInstanceRepository, TaskRepository taskRepository, FlowCache flowCache, FlowRepository flowRepository, AtlasService atlasService, ExperimentRepository experimentRepository, ExperimentProcessValidator experimentProcessValidator, FileProcessValidator fileProcessValidator, ExperimentTaskFeatureRepository taskExperimentFeatureRepository, FlowInstanceService flowInstanceService) {
        this.globals = globals;
        this.flowYamlUtils = flowYamlUtils;
        this.experimentService = experimentService;
        this.binaryDataService = binaryDataService;
        this.experimentProcessService = experimentProcessService;
        this.fileProcessService = fileProcessService;
        this.flowInstanceRepository = flowInstanceRepository;
        this.taskRepository = taskRepository;
        this.flowCache = flowCache;
        this.flowRepository = flowRepository;
        this.atlasService = atlasService;
        this.experimentRepository = experimentRepository;
        this.experimentProcessValidator = experimentProcessValidator;
        this.fileProcessValidator = fileProcessValidator;
        this.taskExperimentFeatureRepository = taskExperimentFeatureRepository;
        this.flowInstanceService = flowInstanceService;
    }

    // TODO need to check all numbers of errors. Must be as #701.xx

    public FlowInstance toStarted(FlowInstance flowInstance) {
        FlowInstance fi = flowInstanceRepository.findById(flowInstance.getId()).orElse(null);
        if (fi==null) {
            String es = "#701.01 Can't change exec state to PRODUCED for flowInstance #" + flowInstance.getId();
            log.error(es);
            throw new IllegalStateException(es);
        }
        Flow flow = flowCache.findById(fi.getFlowId());
        if (flow==null) {
            flowInstance.setExecState(Enums.FlowInstanceExecState.ERROR.code);
            flowInstanceRepository.save(fi);
            return null;
        }
        fi.setExecState(Enums.FlowInstanceExecState.STARTED.code);
        return flowInstanceRepository.save(fi);
    }

    public synchronized void createAllTasks() {

        Monitoring.log("##019", Enums.Monitor.MEMORY);
        List<FlowInstance> flowInstances = flowInstanceRepository.findByExecState(
                Enums.FlowInstanceExecState.PRODUCING.code);
        Monitoring.log("##020", Enums.Monitor.MEMORY);
        if (!flowInstances.isEmpty()) {
            log.info("Start producing tasks");
        }
        for (FlowInstance flowInstance : flowInstances) {
            Flow flow = flowCache.findById(flowInstance.getFlowId());
            if (flow==null) {
                flowInstance.setExecState(Enums.FlowInstanceExecState.ERROR.code);
                flowInstanceRepository.save(flowInstance);
                continue;
            }
            Monitoring.log("##021", Enums.Monitor.MEMORY);
            log.info("Producing tasks for flow.code: {}, input resource pool: \n{}",flow.code, flowInstance.inputResourceParam);
            produceAllTasks(true, flow, flowInstance);
            Monitoring.log("##022", Enums.Monitor.MEMORY);
        }
        if (!flowInstances.isEmpty()) {
            log.info("Producing of tasks was finished");
        }
    }

    public void deleteFlowInstance(Long flowInstanceId, long flowId) {
        experimentService.resetExperiment(flowInstanceId);
        flowInstanceService.deleteById(flowInstanceId);
        taskExperimentFeatureRepository.deleteByFlowInstanceId(flowInstanceId);
        binaryDataService.deleteByFlowInstanceId(flowInstanceId);
        List<FlowInstance> instances = flowInstanceRepository.findByFlowId(flowId);
        if (instances.isEmpty()) {
            Flow flow = flowRepository.findById(flowId).orElse(null);
            if (flow!=null) {
                flow.locked = false;
                flowCache.save(flow);
            }}
    }

    public FlowData.FlowInstanceResult prepareModel(Long flowId, Long flowInstanceId) {
        if (flowId==null) {
            return new FlowData.FlowInstanceResult("#560.83 flow wasn't found, flowId: " + flowId);
        }
        if (flowInstanceId==null) {
            return new FlowData.FlowInstanceResult("#560.85 flowInstanceId is null");
        }
        final FlowInstance flowInstance = flowInstanceRepository.findById(flowInstanceId).orElse(null);
        if (flowInstance == null) {
            return new FlowData.FlowInstanceResult("#560.87 flowInstance wasn't found, flowInstanceId: " + flowInstanceId);
        }
        Flow flow = flowCache.findById(flowId);
        if (flow == null) {
            return new FlowData.FlowInstanceResult("#560.89 flow wasn't found, flowId: " + flowId);
        }

        if (!flow.getId().equals(flowInstance.flowId)) {
            flowInstance.valid=false;
            flowInstanceRepository.save(flowInstance);
            return new FlowData.FlowInstanceResult("#560.73 flowId doesn't match to flowInstance.flowId, flowId: " + flowId+", flowInstance.flowId: " + flowInstance.flowId);
        }

        FlowData.FlowInstanceResult result = new FlowData.FlowInstanceResult(flow, flowInstance);
        return result;
    }

    public FlowData.FlowValidation validateInternal(Flow flow) {
        final FlowData.FlowValidation flowValidation = new FlowData.FlowValidation();
        try {
            flowValidation.status = validate(flow);
        } catch (YAMLException e) {
            flowValidation.addErrorMessage("#560.34 Error while parsing yaml config, " + e.toString());
            flowValidation.status = Enums.FlowValidateStatus.YAML_PARSING_ERROR;
        }
        flow.valid = flowValidation.status == Enums.FlowValidateStatus.OK;
        flowCache.save(flow);
        if (flow.valid) {
            flowValidation.infoMessages = Collections.singletonList("Validation result: OK");
        }
        else {
            log.error("Validation error: {}", flowValidation.status);
            flowValidation.addErrorMessage("#561.01 Validation error: : " + flowValidation.status);
        }
        return flowValidation;
    }

    public OperationStatusRest flowInstanceTargetExecState(
            Long flowId, Long flowInstanceId, Enums.FlowInstanceExecState execState) {
        FlowData.FlowInstanceResult result = prepareModel(flowId, flowInstanceId);
        if (result.isErrorMessages()) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR, result.errorMessages);
        }

        if (result.flow==null || result.flowInstance==null) {
            throw new IllegalStateException("Error: (result.flow==null || result.flowInstance==null)");
        }

        result.flowInstance.setExecState(execState.code);
        flowInstanceRepository.save(result.flowInstance);

        result.flow.locked = true;
        flowCache.save(result.flow);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public FlowData.FlowInstancesResult getFlowInstancesResult(@PathVariable Long id, @PageableDefault(size = 5) Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.flowInstanceRowsLimit, pageable);
        FlowData.FlowInstancesResult result = new FlowData.FlowInstancesResult();
        result.instances = flowInstanceRepository.findByFlowId(pageable, id);
        result.currentFlowId = id;

        for (FlowInstance flowInstance : result.instances) {
            Flow flow = flowCache.findById(flowInstance.getFlowId());
            if (flow==null) {
                log.warn("#560.51 Found flowInstance with wrong flowId. flowId: {}", flowInstance.getFlowId());
                continue;
            }
            result.flows.put(flowInstance.getId(), flow);
        }
        return result;
    }

    @Data
    @NoArgsConstructor
    public static class TaskProducingResult {
        public Enums.FlowValidateStatus flowValidateStatus = Enums.FlowValidateStatus.NOT_VALIDATED_YET_ERROR;
        public Enums.FlowProducingStatus flowProducingStatus = Enums.FlowProducingStatus.NOT_PRODUCING_YET_ERROR;
        public List<Task> tasks = new ArrayList<>();
        public FlowYaml flowYaml;
        public FlowInstance flowInstance;
        public int numberOfTasks;

        public Enums.TaskProducingStatus getStatus() {
            if (flowValidateStatus != Enums.FlowValidateStatus.OK) {
                return Enums.TaskProducingStatus.VERIFY_ERROR;
            }
            if (flowProducingStatus!= Enums.FlowProducingStatus.OK) {
                return Enums.TaskProducingStatus.PRODUCING_ERROR;
            }
            return Enums.TaskProducingStatus.OK;
        }
    }

    public TaskProducingResult produceAllTasks(boolean isPersist, Flow flow, FlowInstance flowInstance ) {
        TaskProducingResult result = new TaskProducingResult();
        if (isPersist && flowInstance.getExecState()!=Enums.FlowInstanceExecState.PRODUCING.code) {
            result.flowValidateStatus = Enums.FlowValidateStatus.ALREADY_PRODUCED_ERROR;
            return result;
        }
        long mills = System.currentTimeMillis();
        result.flowValidateStatus = validate(flow);
        log.info("Flow was validated for "+(System.currentTimeMillis() - mills) + " ms.");
        if (result.flowValidateStatus != Enums.FlowValidateStatus.OK &&
                result.flowValidateStatus != Enums.FlowValidateStatus.EXPERIMENT_ALREADY_STARTED_ERROR ) {
            log.error("#701.07 Can't produce tasks, error: {}", result.flowValidateStatus);
            toStopped(isPersist, flowInstance.getId());
            return result;
        }
        Monitoring.log("##022", Enums.Monitor.MEMORY);
        mills = System.currentTimeMillis();
        produceTasks(isPersist, result, flow, flowInstance);
        log.info("FlowService.produceTasks() was processed for "+(System.currentTimeMillis() - mills) + " ms.");
        Monitoring.log("##033", Enums.Monitor.MEMORY);

        return result;
    }

    public Enums.FlowValidateStatus validate(Flow flow) {
        if (flow==null) {
            return Enums.FlowValidateStatus.NO_ANY_PROCESSES_ERROR;
        }
        if (StringUtils.isBlank(flow.code)) {
            return Enums.FlowValidateStatus.FLOW_CODE_EMPTY_ERROR;
        }
        if (StringUtils.isBlank(flow.params)) {
            return Enums.FlowValidateStatus.FLOW_PARAMS_EMPTY_ERROR;
        }
        FlowYaml flowYaml = flowYamlUtils.toFlowYaml(flow.params);
        if (flowYaml.getProcesses().isEmpty()) {
            return Enums.FlowValidateStatus.NO_ANY_PROCESSES_ERROR;
        }
/*
        if (StringUtils.isBlank(flowYaml.getProcesses().get(0).inputType)) {
            return Enums.FlowValidateStatus.INPUT_TYPE_EMPTY_ERROR;
        }
*/

        FlowYaml fl = flowYamlUtils.toFlowYaml(flow.getParams());

        Process lastProcess = null;
        boolean experimentPresent = false;
        List<Process> processes = fl.getProcesses();
        for (int i = 0; i < processes.size(); i++) {
            Process process = processes.get(i);
            if (i+1<processes.size() && StringUtils.isBlank(process.getOutputType())) {
                return Enums.FlowValidateStatus.OUTPUT_TYPE_EMPTY_ERROR;
            }
            lastProcess = process;
            if (StringUtils.containsWhitespace(process.code) || StringUtils.contains(process.code, ',') ){
                return Enums.FlowValidateStatus.PROCESS_CODE_CONTAINS_ILLEGAL_CHAR_ERROR;
            }
            if (StringUtils.containsWhitespace(process.inputResourceCode) || StringUtils.contains(process.inputResourceCode, ',') ){
                return Enums.FlowValidateStatus.RESOURCE_CODE_CONTAINS_ILLEGAL_CHAR_ERROR;
            }
            if (StringUtils.containsWhitespace(process.outputResourceCode) || StringUtils.contains(process.outputResourceCode, ',') ){
                return Enums.FlowValidateStatus.RESOURCE_CODE_CONTAINS_ILLEGAL_CHAR_ERROR;
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
            Enums.FlowValidateStatus status = processValidator.validate(flow, process, i==0);
            if (status!=null) {
                return status;
            }

            if (process.parallelExec && (process.snippetCodes==null || process.snippetCodes.size()<2)) {
                return Enums.FlowValidateStatus.NOT_ENOUGH_FOR_PARALLEL_EXEC_ERROR;
            }
        }
        if (experimentPresent && lastProcess.type!= EnumsApi.ProcessType.EXPERIMENT) {
            return  Enums.FlowValidateStatus.EXPERIMENT_MUST_BE_LAST_PROCESS_ERROR;
        }
        return Enums.FlowValidateStatus.OK;
    }

    @Data
    public static class ProduceTaskResult {
        public Enums.FlowProducingStatus status;
        public List<String> outputResourceCodes;
        public int numberOfTasks;
    }

    public FlowService.TaskProducingResult createFlowInstance(Flow flow, String inputResourceParam) {
        FlowService.TaskProducingResult result = new TaskProducingResult();

        InputResourceParam resourceParam = InputResourceParamUtils.to(inputResourceParam);
        List<SimpleCodeAndStorageUrl> inputResourceCodes = binaryDataService.getResourceCodesInPool(resourceParam.getAllPoolCodes());
        if (inputResourceCodes==null || inputResourceCodes.isEmpty()) {
            result.flowProducingStatus = Enums.FlowProducingStatus.INPUT_POOL_CODE_DOESNT_EXIST_ERROR;
            return result;
        }

        FlowInstance fi = new FlowInstance();
        fi.setFlowId(flow.getId());
        fi.setCreatedOn(System.currentTimeMillis());
        fi.setExecState(Enums.FlowInstanceExecState.NONE.code);
        fi.setCompletedOn(null);
        fi.setInputResourceParam(inputResourceParam);
        fi.setProducingOrder(Consts.TASK_ORDER_START_VALUE);
        fi.setValid(true);

        flowInstanceRepository.save(fi);
        result.flowProducingStatus = Enums.FlowProducingStatus.OK;
        result.flowInstance = fi;

        return result;
    }

    public void toStopped(boolean isPersist, long flowInstanceId) {
        if (!isPersist) {
            return;
        }
        FlowInstance fi = flowInstanceRepository.findById(flowInstanceId).orElse(null);
        if (fi==null) {
            return;
        }
        fi.setExecState(Enums.FlowInstanceExecState.STOPPED.code);
        flowInstanceRepository.save(fi);
    }

    public void changeValidStatus(FlowInstance fi, boolean status) {
        fi.setValid(status);
        flowInstanceRepository.save(fi);
    }

    public Enums.FlowProducingStatus toProducing(FlowInstance fi) {
        fi.setExecState(Enums.FlowInstanceExecState.PRODUCING.code);
        flowInstanceRepository.save(fi);
        return Enums.FlowProducingStatus.OK;
    }

    @Data
    @NoArgsConstructor
    public static class ResourcePools {
        public final Map<String, List<String>> collectedInputs = new HashMap<>();
        public Map<String, String> inputStorageUrls=null;
        public Enums.FlowProducingStatus status = Enums.FlowProducingStatus.OK;

        public ResourcePools(List<SimpleCodeAndStorageUrl> initialInputResourceCodes) {

            if (initialInputResourceCodes==null || initialInputResourceCodes.isEmpty()) {
                status = Enums.FlowProducingStatus.INPUT_POOL_CODE_DOESNT_EXIST_ERROR;
                return;
            }

            initialInputResourceCodes.forEach(o-> {
                collectedInputs.computeIfAbsent(o.poolCode, p -> new ArrayList<>()).add(o.code);
            });

            inputStorageUrls = initialInputResourceCodes
                    .stream()
                    .collect(Collectors.toMap(o -> o.code, o -> o.storageUrl));

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

    @SuppressWarnings("Duplicates")
    public void produceTasks(boolean isPersist, TaskProducingResult result, Flow flow, FlowInstance fi) {

        Monitoring.log("##023", Enums.Monitor.MEMORY);
        long mill = System.currentTimeMillis();
        InputResourceParam resourceParams = InputResourceParamUtils.to(fi.inputResourceParam);
        List<SimpleCodeAndStorageUrl> initialInputResourceCodes;
        initialInputResourceCodes = binaryDataService.getResourceCodesInPool(resourceParams.getAllPoolCodes());
        log.info("Resources was acquired for " + (System.currentTimeMillis() - mill) +" ms" );

        ResourcePools pools = new ResourcePools(initialInputResourceCodes);
        if (pools.status!= Enums.FlowProducingStatus.OK) {
            result.flowProducingStatus = pools.status;
            return;
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
                    result.flowProducingStatus = Enums.FlowProducingStatus.ERROR;
                    return;
                }
                collectedInputs.put(newKey, value);
            });
            pools.collectedInputs.clear();
            pools.collectedInputs.putAll(collectedInputs);
        }

        Monitoring.log("##025", Enums.Monitor.MEMORY);

        result.flowInstance = fi;

        result.flowYaml = flowYamlUtils.toFlowYaml(flow.getParams());
        flow.clean = result.flowYaml.clean;
        int idx = Consts.TASK_ORDER_START_VALUE;
        result.flowProducingStatus = Enums.FlowProducingStatus.OK;
        for (Process process : result.flowYaml.getProcesses()) {
            process.order = idx++;

            ProduceTaskResult produceTaskResult;
            switch(process.type) {
                case FILE_PROCESSING:
                    Monitoring.log("##026", Enums.Monitor.MEMORY);
                    produceTaskResult = fileProcessService.produceTasks(isPersist, flow, fi, process, pools);
                    Monitoring.log("##027", Enums.Monitor.MEMORY);
                    break;
                case EXPERIMENT:
                    Monitoring.log("##028", Enums.Monitor.MEMORY);
                    produceTaskResult = experimentProcessService.produceTasks(isPersist, flow, fi, process, pools);
                    Monitoring.log("##029", Enums.Monitor.MEMORY);
                    break;
                default:
                    throw new IllegalStateException("#701.11 Unknown process type");
            }
            result.numberOfTasks += produceTaskResult.numberOfTasks;
            if (produceTaskResult.status != Enums.FlowProducingStatus.OK) {
                result.flowProducingStatus = produceTaskResult.status;
                return;
            }
            if (!process.collectResources) {
                pools.clean();
            }
            Monitoring.log("##030", Enums.Monitor.MEMORY);
            pools.add(process.outputType, produceTaskResult.outputResourceCodes);
            Monitoring.log("##031", Enums.Monitor.MEMORY);
        }
        toProduced(isPersist, result, fi);

        result.flowProducingStatus = Enums.FlowProducingStatus.OK;

    }

    private void toProduced(boolean isPersist, TaskProducingResult result, FlowInstance fi) {
        if (!isPersist) {
            return;
        }
        Long id = fi.getId();
        result.flowInstance = flowInstanceRepository.findById(id).orElse(null);
        if (result.flowInstance==null) {
            String es = "#701.16 Can't change exec state to PRODUCED for flowInstance #" + id;
            log.error(es);
            throw new IllegalStateException(es);
        }
        result.flowInstance.setExecState(Enums.FlowInstanceExecState.PRODUCED.code);
        flowInstanceRepository.save(result.flowInstance);
    }

    public void markOrderAsProcessed() {
        List<FlowInstance> flowInstances = flowInstanceRepository.findByExecState(Enums.FlowInstanceExecState.STARTED.code);
        for (FlowInstance flowInstance : flowInstances) {
            markOrderAsProcessed(flowInstance);
        }
    }

    public FlowInstance markOrderAsProcessed(FlowInstance flowInstance) {
        List<Long> anyTask = taskRepository.findAnyNotAssignedWithConcreteOrder(Consts.PAGE_REQUEST_1_REC, flowInstance.getId(), flowInstance.getProducingOrder() );
        if (!anyTask.isEmpty()) {
            return flowInstance;
        }
        List<Task> forChecking = taskRepository.findWithConcreteOrder(flowInstance.getId(), flowInstance.getProducingOrder() );
        if (forChecking.isEmpty()) {
            Long count = taskRepository.countWithConcreteOrder(flowInstance.getId(), flowInstance.getProducingOrder() + 1);
            if (count==null) {
                throw new IllegalStateException("#701.21 count of records is null");
            }
            if (count==0) {
                log.info("FlowInstance #{} was finished", flowInstance.getId());
                experimentService.updateMaxValueForExperimentFeatures(flowInstance.getId());
                flowInstance.setCompletedOn(System.currentTimeMillis());
                flowInstance.setExecState(Enums.FlowInstanceExecState.FINISHED.code);
                FlowInstance instance = flowInstanceRepository.save(flowInstance);

                Experiment e = experimentRepository.findByFlowInstanceId(instance.id);
                if (e==null) {
                    log.info("#701.23 Can't store an experiment to atlas, the flowInstance "+instance.id+" doesn't contain an experiment" );
                    return instance;
                }
                atlasService.toAtlas(instance.id, e.getId());
                return instance;
            }
            return flowInstance;
        }
        for (Task task : forChecking) {
            if (!task.isCompleted()) {
                return flowInstance;
            }
        }
        flowInstance.setProducingOrder(flowInstance.getProducingOrder()+1);
        return flowInstanceRepository.save(flowInstance);
    }

    public static String asInputResourceParams(String poolCode) {
        return "poolCodes:\n  "+Consts.FLOW_INSTANCE_INPUT_TYPE+":\n" +
                "  - " + poolCode;
    }
}
