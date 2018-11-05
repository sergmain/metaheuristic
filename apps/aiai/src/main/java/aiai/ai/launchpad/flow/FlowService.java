package aiai.ai.launchpad.flow;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.launchpad.experiment.ExperimentProcessService;
import aiai.ai.launchpad.file_process.FileProcessService;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.repositories.FlowRepository;
import aiai.ai.yaml.flow.FlowYaml;
import aiai.ai.yaml.flow.FlowYamlUtils;
import aiai.ai.launchpad.Process;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FlowService {

    private final Globals globals;
    private final FlowRepository flowRepository;
    private final FlowYamlUtils flowYamlUtils;

    private final ExperimentProcessService experimentProcessService;
    private final FileProcessService fileProcessService;

    public FlowService(Globals globals, FlowRepository flowRepository, FlowYamlUtils flowYamlUtils, ExperimentProcessService experimentProcessService, FileProcessService fileProcessService) {
        this.globals = globals;
        this.flowRepository = flowRepository;
        this.flowYamlUtils = flowYamlUtils;
        this.experimentProcessService = experimentProcessService;
        this.fileProcessService = fileProcessService;
    }

    public enum TaskProducingStatus { OK, VERIFY_ERROR, PRODUCING_ERROR }
    public enum FlowVerifyStatus { OK,
        NOT_VERIFIED_YET_ERROR,
        FLOW_CODE_EMPTY_ERROR,
        NO_INPUT_POOL_CODE_ERROR,
        NO_ANY_PROCESSES_ERROR,
        INPUT_TYPE_EMPTY_ERROR,
        NOT_ENOUGH_FOR_PARALLEL_EXEC_ERROR,
        SNIPPET_NOT_FOUND_ERROR,
        FLOW_PARAMS_EMPTY_ERROR,
        SNIPPET_ALREADY_PROVIDED_BY_EXPERIMENT_ERROR,
        PROCESS_CODE_NOT_FOUND_ERROR,
        TOO_MANY_SNIPPET_CODES_ERROR,
    }

    public enum FlowProducingStatus { OK,
        NOT_PRODUCING_YET_ERROR,
        ERROR,
    }

    @Data
    @NoArgsConstructor
    public static class TaskProducingResult {
        public FlowVerifyStatus flowVerifyStatus = FlowVerifyStatus.NOT_VERIFIED_YET_ERROR ;
        public FlowProducingStatus flowProducingStatus = FlowProducingStatus.NOT_PRODUCING_YET_ERROR;
        public List<Task> tasks = new ArrayList<>();
        public FlowYaml flowYaml;

        public TaskProducingStatus getStatus() {
            if (flowVerifyStatus != FlowVerifyStatus.OK) {
                return TaskProducingStatus.VERIFY_ERROR;
            }
            if (flowProducingStatus!=FlowProducingStatus.OK) {
                return TaskProducingStatus.PRODUCING_ERROR;
            }
            return TaskProducingStatus.OK;
        }
    }

    public TaskProducingResult createTasks(Flow flow) {
        TaskProducingResult result = new TaskProducingResult();
        result.flowVerifyStatus = verify(flow);
        if (result.flowVerifyStatus != FlowVerifyStatus.OK) {
            return result;
        }
        result.flowProducingStatus = produce(flow);

        return result;
    }

    private FlowProducingStatus produce(Flow flow) {
        return null;
    }

    private FlowVerifyStatus verify(Flow flow) {
        if (flow==null) {
            return FlowVerifyStatus.NO_ANY_PROCESSES_ERROR;
        }
        if (StringUtils.isBlank(flow.code)) {
            return FlowVerifyStatus.FLOW_CODE_EMPTY_ERROR;
        }
        if (StringUtils.isBlank(flow.params)) {
            return FlowVerifyStatus.FLOW_PARAMS_EMPTY_ERROR;
        }
        if (StringUtils.isBlank(flow.inputPoolCode) ) {
            return FlowVerifyStatus.NO_INPUT_POOL_CODE_ERROR;
        }
        FlowYaml flowYaml = flowYamlUtils.toFlowYaml(flow.params);
        if (flowYaml.getProcesses().isEmpty()) {
            return FlowVerifyStatus.NO_ANY_PROCESSES_ERROR;
        }
        if (StringUtils.isBlank(flowYaml.getProcesses().get(0).inputType)) {
            return FlowVerifyStatus.INPUT_TYPE_EMPTY_ERROR;
        }

        FlowYaml fl = flowYamlUtils.toFlowYaml(flow.getParams());

        for (Process process : fl.getProcesses()) {
            if (process.getSnippetCodes()==null || process.getSnippetCodes().isEmpty()) {
                return FlowVerifyStatus.SNIPPET_NOT_FOUND_ERROR;
            }
            if (process.parallelExec && process.snippetCodes.size()<2) {
                return FlowVerifyStatus.NOT_ENOUGH_FOR_PARALLEL_EXEC_ERROR;
            }
            if (process.type == Enums.ProcessType.EXPERIMENT) {
                if (process.snippetCodes.size() > 0) {
                    return FlowVerifyStatus.SNIPPET_ALREADY_PROVIDED_BY_EXPERIMENT_ERROR;
                }
                if (StringUtils.isBlank(process.code)) {
                    return FlowVerifyStatus.SNIPPET_NOT_FOUND_ERROR;
                }
            }
            if (process.type== Enums.ProcessType.FILE_PROCESSING) {
                if (!process.parallelExec && process.snippetCodes.size()>1) {
                    return FlowVerifyStatus.TOO_MANY_SNIPPET_CODES_ERROR;
                }
            }
        }
        return FlowVerifyStatus.OK;
    }

    private void produce(TaskProducingResult result, Flow flow) {
        result.flowYaml = flowYamlUtils.toFlowYaml(flow.getParams());
        Process prev = null;
        int idx = 0;
        for (Process process : result.flowYaml.getProcesses()) {
            ++idx;
            switch(process.type) {
                case FILE_PROCESSING:
                    fileProcessService.produceTasks(flow, prev, process, idx);
                    break;
                case EXPERIMENT:
                    experimentProcessService.produceTasks(flow, prev, process, idx);
                    break;
                default:
                    throw new IllegalStateException("Unknown process type");
            }
            prev = process;
        }
    }
}
