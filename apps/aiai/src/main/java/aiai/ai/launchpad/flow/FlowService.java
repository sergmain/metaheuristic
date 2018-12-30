package aiai.ai.launchpad.flow;

import aiai.ai.Consts;
import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.Monitoring;
import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.beans.Task;
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
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static aiai.ai.Enums.FlowValidateStatus.PROCESS_VALIDATOR_NOT_FOUND_ERROR;

@Service
@Slf4j
@Profile("launchpad")
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

    public FlowInstance toStarted(FlowInstance flowInstance) {
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
            log.info("Producing tasks for flow.code: {}, input resource pool: {}",flow.code, flowInstance.inputResourcePoolCode);
            produceAllTasks(true, flow, flowInstance);
            Monitoring.log("##022", Enums.Monitor.MEMORY);
        }
        if (!flowInstances.isEmpty()) {
            log.info("Producing tasks was finished");
        }
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
            log.error("Can't produce tasks, error: {}", result.flowValidateStatus);
            toStopped(isPersist, flowInstance.getId());
            return result;
        }
        Monitoring.log("##022", Enums.Monitor.MEMORY);
        mills = System.currentTimeMillis();
        produce(isPersist, result, flow, flowInstance);
        log.info("FlowService.produce() was processed for "+(System.currentTimeMillis() - mills) + " ms.");
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
            Enums.FlowValidateStatus status = processValidator.validate(flow, process);
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
        public int numberOfTasks;
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

    public void toStopped(boolean isPersist, long flowInstanceId) {
        if (!isPersist) {
            return;
        }
        FlowInstance fi = flowInstanceRepository.findById(flowInstanceId).orElse(null);
        if (fi==null) {
            return;
        }
        fi.setExecState(Enums.FlowInstanceExecState.STOPPED.code);
        fi.setProducingOrder(0);
        flowInstanceRepository.save(fi);
    }

    public void changeValidStatus(FlowInstance fi, boolean status) {
        fi.setValid(status);
        flowInstanceRepository.save(fi);
    }

    public Enums.FlowProducingStatus toProducing(FlowInstance fi) {
        fi.setExecState(Enums.FlowInstanceExecState.PRODUCING.code);
        fi.setProducingOrder(0);
        flowInstanceRepository.save(fi);
        return Enums.FlowProducingStatus.OK;
    }

    public void produce(boolean isPersist, TaskProducingResult result, Flow flow, FlowInstance fi) {

        final Map<String, List<String>> collectedInputs = new HashMap<>();

        Monitoring.log("##023", Enums.Monitor.MEMORY);
        long mill = System.currentTimeMillis();
        List<String> inputResourceCodes = binaryDataService.getResourceCodesInPool(fi.inputResourcePoolCode);
        log.info("Resources was acquired for " + (System.currentTimeMillis() - mill) +" ms" );
        Monitoring.log("##024", Enums.Monitor.MEMORY);

        if (inputResourceCodes==null || inputResourceCodes.isEmpty()) {
            result.flowProducingStatus = Enums.FlowProducingStatus.INPUT_POOL_CODE_DOESNT_EXIST_ERROR;
            return;
        }
        collectedInputs.computeIfAbsent(Consts.FLOW_INSTANCE_INPUT_TYPE, k -> new ArrayList<>()).addAll(inputResourceCodes);
        Monitoring.log("##025", Enums.Monitor.MEMORY);

        result.flowInstance = fi;

        result.flowYaml = flowYamlUtils.toFlowYaml(flow.getParams());
        flow.clean = result.flowYaml.clean;
        int idx = 0;
        result.flowProducingStatus = Enums.FlowProducingStatus.OK;
        for (Process process : result.flowYaml.getProcesses()) {
            ++idx;
            process.order = idx;

            ProduceTaskResult produceTaskResult;
            switch(process.type) {
                case FILE_PROCESSING:
                    Monitoring.log("##026", Enums.Monitor.MEMORY);
                    produceTaskResult = fileProcessService.produceTasks(isPersist, flow, fi, process, collectedInputs);
                    Monitoring.log("##027", Enums.Monitor.MEMORY);
                    break;
                case EXPERIMENT:
                    Monitoring.log("##028", Enums.Monitor.MEMORY);
                    produceTaskResult = experimentProcessService.produceTasks(isPersist, flow, fi, process, collectedInputs);
                    Monitoring.log("##029", Enums.Monitor.MEMORY);
                    break;
                default:
                    throw new IllegalStateException("Unknown process type");
            }
            result.numberOfTasks += produceTaskResult.numberOfTasks;
            if (produceTaskResult.status != Enums.FlowProducingStatus.OK) {
                result.flowProducingStatus = produceTaskResult.status;
                return;
            }
            if (!process.collectResources) {
                collectedInputs.clear();
            }
            Monitoring.log("##030", Enums.Monitor.MEMORY);
            if (produceTaskResult.outputResourceCodes!=null) {
                for (String outputResourceCode : produceTaskResult.outputResourceCodes) {
                    List<String> list = collectedInputs.computeIfAbsent(process.outputType, k -> new ArrayList<>());
                    list.add(outputResourceCode);
                }
            }
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
            String es = "Can't change exec state to PRODUCED for flowInstance #" + id;
            log.error(es);
            throw new IllegalStateException(es);
        }
        result.flowInstance.setExecState(Enums.FlowInstanceExecState.PRODUCED.code);
        flowInstanceRepository.save(result.flowInstance);
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
            Long count = taskRepository.countWithConcreteOrder(flowInstance.getId(), flowInstance.getProducingOrder() + 1);
            if (count==null) {
                throw new IllegalStateException("count of records is null");
            }
            if (count==0) {
                log.info("FlowInstance #{} was finished", flowInstance.getId());
                flowInstance.setCompletedOn(System.currentTimeMillis());
                flowInstance.setExecState(Enums.FlowInstanceExecState.FINISHED.code);
                flowInstanceRepository.save(flowInstance);
            }
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
