package aiai.ai.launchpad.flow;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.*;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.experiment.ExperimentCache;
import aiai.ai.launchpad.experiment.ExperimentProcessService;
import aiai.ai.launchpad.experiment.ExperimentProcessValidator;
import aiai.ai.launchpad.file_process.FileProcessService;
import aiai.ai.launchpad.file_process.FileProcessValidator;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.FlowRepository;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.ai.yaml.flow.FlowYaml;
import aiai.ai.yaml.flow.FlowYamlUtils;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static aiai.ai.Enums.FlowValidateStatus.PROCESS_VALIDATOR_NOT_FOUND_ERROR;

@Service
@Slf4j
public class FlowService {

    private final FlowYamlUtils flowYamlUtils;
    private final ExperimentCache experimentCache;
    private final BinaryDataService binaryDataService;

    private final ExperimentProcessService experimentProcessService;
    private final FileProcessService fileProcessService;
    private final FlowInstanceRepository flowInstanceRepository;
    private final SnippetCache snippetCache;
    private final TaskRepository taskRepository;
    private final FlowCache flowCache;

    private final ExperimentProcessValidator experimentProcessValidator;
    private final FileProcessValidator fileProcessValidator;

    public FlowService(Globals globals, FlowRepository flowRepository, FlowYamlUtils flowYamlUtils, ExperimentCache experimentCache, BinaryDataService binaryDataService, ExperimentProcessService experimentProcessService, FileProcessService fileProcessService, FlowInstanceRepository flowInstanceRepository, SnippetCache snippetCache, TaskRepository taskRepository, FlowCache flowCache, ExperimentProcessValidator experimentProcessValidator, FileProcessValidator fileProcessValidator) {
        this.flowYamlUtils = flowYamlUtils;
        this.experimentCache = experimentCache;
        this.binaryDataService = binaryDataService;
        this.experimentProcessService = experimentProcessService;
        this.fileProcessService = fileProcessService;
        this.flowInstanceRepository = flowInstanceRepository;
        this.snippetCache = snippetCache;
        this.taskRepository = taskRepository;
        this.flowCache = flowCache;
        this.experimentProcessValidator = experimentProcessValidator;
        this.fileProcessValidator = fileProcessValidator;
    }

    public FlowInstance startFlowInstance(FlowInstance flowInstance) {
        FlowInstance fi = flowInstanceRepository.findById(flowInstance.getId()).orElse(null);
        if (fi==null) {
            String es = "Can't change exec state to PRODUCED for flowInstance #" + flowInstance.getId();
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
        log.info("Start producing tasks");
        List<FlowInstance> flowInstances = flowInstanceRepository.findByExecState(
                Enums.FlowInstanceExecState.PRODUCING.code);
        for (FlowInstance flowInstance : flowInstances) {
            Flow flow = flowCache.findById(flowInstance.getFlowId());
            if (flow==null) {
                flowInstance.setExecState(Enums.FlowInstanceExecState.ERROR.code);
                flowInstanceRepository.save(flowInstance);
                continue;
            }
            log.info("Producing tasks for flow.code: {}, input resource pool: {}",flow.code, flowInstance.inputResourcePoolCode);
            createTasks(flow, flowInstance);
        }
        log.info("Producing tasks was finished");
    }

    @Data
    @NoArgsConstructor
    public static class TaskProducingResult {
        public Enums.FlowValidateStatus flowVerifyStatus = Enums.FlowValidateStatus.NOT_VALIDATED_YET_ERROR;
        public Enums.FlowProducingStatus flowProducingStatus = Enums.FlowProducingStatus.NOT_PRODUCING_YET_ERROR;
        public List<Task> tasks = new ArrayList<>();
        public FlowYaml flowYaml;
        public FlowInstance flowInstance;

        public Enums.TaskProducingStatus getStatus() {
            if (flowVerifyStatus != Enums.FlowValidateStatus.OK) {
                return Enums.TaskProducingStatus.VERIFY_ERROR;
            }
            if (flowProducingStatus!= Enums.FlowProducingStatus.OK) {
                return Enums.TaskProducingStatus.PRODUCING_ERROR;
            }
            return Enums.TaskProducingStatus.OK;
        }
    }

    public TaskProducingResult createTasks(Flow flow, FlowInstance flowInstance ) {
        TaskProducingResult result = new TaskProducingResult();
        if (flowInstance.getExecState()!=Enums.FlowInstanceExecState.PRODUCING.code) {
            result.flowVerifyStatus = Enums.FlowValidateStatus.ALREADY_PRODUCED_ERROR;
            return result;
        }
        result.flowVerifyStatus = validate(flow);
        if (result.flowVerifyStatus != Enums.FlowValidateStatus.OK) {
            log.error("Can't produce tasks, error: {}", result.flowVerifyStatus);
            return result;
        }
        produce(result, flow, flowInstance);

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
        if (StringUtils.isBlank(flowYaml.getProcesses().get(0).inputType)) {
            return Enums.FlowValidateStatus.INPUT_TYPE_EMPTY_ERROR;
        }

        FlowYaml fl = flowYamlUtils.toFlowYaml(flow.getParams());

        Process lastProcess = null;
        boolean experimentPresent = false;
        for (Process process : fl.getProcesses()) {
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
            if (process.type == Enums.ProcessType.EXPERIMENT) {
                experimentPresent = true;
                processValidator = experimentProcessValidator;
            }
            else if (process.type == Enums.ProcessType.FILE_PROCESSING) {
                processValidator = fileProcessValidator;
            }
            else {
                return PROCESS_VALIDATOR_NOT_FOUND_ERROR;
            }
            Enums.FlowValidateStatus status = processValidator.validate(process);
            if (status!=null) {
                return status;
            }

            if (process.parallelExec && (process.snippetCodes==null || process.snippetCodes.size()<2)) {
                return Enums.FlowValidateStatus.NOT_ENOUGH_FOR_PARALLEL_EXEC_ERROR;
            }
        }
        if (experimentPresent && lastProcess.type!=Enums.ProcessType.EXPERIMENT) {
            return  Enums.FlowValidateStatus.EXPERIMENT_MUST_BE_LAST_PROCESS_ERROR;
        }
        return Enums.FlowValidateStatus.OK;
    }

    @Data
    public static class ProduceTaskResult {
        public Enums.FlowProducingStatus status;
        public List<String> outputResourceCodes;
    }

    public FlowService.TaskProducingResult createFlowInstance(Flow flow, String startWithResourcePoolCode) {
        FlowService.TaskProducingResult result = new TaskProducingResult();
        List<String> inputResourceCodes = binaryDataService.getResourceCodesInPool(startWithResourcePoolCode);
        if (inputResourceCodes==null || inputResourceCodes.isEmpty()) {
            result.flowProducingStatus = Enums.FlowProducingStatus.INPUT_POOL_CODE_DOESNT_EXIST_ERROR;
            return result;
        }

        FlowInstance fi = new FlowInstance();
        fi.setFlowId(flow.getId());
        fi.setCreatedOn(System.currentTimeMillis());
        fi.setExecState(Enums.FlowInstanceExecState.NONE.code);
        fi.setCompletedOn(null);
        fi.setInputResourcePoolCode(startWithResourcePoolCode);
        fi.setProducingOrder(0);
        fi.setValid(true);

        flowInstanceRepository.save(fi);
        result.flowProducingStatus = Enums.FlowProducingStatus.OK;
        result.flowInstance = fi;

        return result;
    }

    public Enums.FlowProducingStatus toProducing(FlowInstance fi) {
        fi.setExecState(Enums.FlowInstanceExecState.PRODUCING.code);
        fi.setProducingOrder(0);
        flowInstanceRepository.save(fi);
        return Enums.FlowProducingStatus.OK;
    }

    public void produce(TaskProducingResult result, Flow flow, FlowInstance fi) {

        List<String> inputResourceCodes = binaryDataService.getResourceCodesInPool(fi.inputResourcePoolCode);
        if (inputResourceCodes==null || inputResourceCodes.isEmpty()) {
            result.flowProducingStatus = Enums.FlowProducingStatus.INPUT_POOL_CODE_DOESNT_EXIST_ERROR;
            return;
        }

        result.flowInstance = fi;

        result.flowYaml = flowYamlUtils.toFlowYaml(flow.getParams());
        int idx = 0;
        result.flowProducingStatus = Enums.FlowProducingStatus.OK;
        List<String> outputResourceCode;
        for (Process process : result.flowYaml.getProcesses()) {
            ++idx;

            ProduceTaskResult produceTaskResult;
            switch(process.type) {
                case FILE_PROCESSING:
                    produceTaskResult = fileProcessService.produceTasks(flow, fi, process, idx, inputResourceCodes);
                    break;
                case EXPERIMENT:
                    produceTaskResult = experimentProcessService.produceTasks(flow, fi, process, idx, inputResourceCodes);
                    break;
                default:
                    throw new IllegalStateException("Unknown process type");
            }
            if (produceTaskResult.status!= Enums.FlowProducingStatus.OK) {
                result.flowProducingStatus = produceTaskResult.status;
                return;
            }
            inputResourceCodes = produceTaskResult.outputResourceCodes;
        }
        Long id = fi.getId();
        result.flowInstance = flowInstanceRepository.findById(id).orElse(null);
        if (result.flowInstance==null) {
            String es = "Can't change exec state to PRODUCED for flowInstance #" + id;
            log.error(es);
            throw new IllegalStateException(es);
        }
        result.flowInstance.setExecState(Enums.FlowInstanceExecState.PRODUCED.code);
        flowInstanceRepository.save(result.flowInstance);

        result.flowProducingStatus = Enums.FlowProducingStatus.OK;

    }

    public void markOrderAsCompleted() {
        List<FlowInstance> flowInstances = flowInstanceRepository.findByExecState(
                Enums.FlowInstanceExecState.STARTED.code);
        for (FlowInstance flowInstance : flowInstances) {
            markOrderAsCompleted(flowInstance);
        }
    }
    private void markOrderAsCompleted(FlowInstance flowInstance) {
        List<Task> forCompletion = taskRepository.findForCompletion(flowInstance.getId(), flowInstance.getProducingOrder() + 1);
        if (forCompletion.isEmpty()) {
            return;
        }
        for (Task task : forCompletion) {
            if (!task.isCompleted) {
                return;
            }
        }
        flowInstance.setProducingOrder(flowInstance.getProducingOrder()+1);
        flowInstanceRepository.save(flowInstance);
    }


}
