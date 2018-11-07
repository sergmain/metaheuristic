package aiai.ai.launchpad.flow;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.experiment.ExperimentProcessService;
import aiai.ai.launchpad.file_process.FileProcessService;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.FlowRepository;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.ai.yaml.flow.FlowYaml;
import aiai.ai.yaml.flow.FlowYamlUtils;
import aiai.ai.launchpad.Process;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class FlowService {

    private final Globals globals;
    private final FlowRepository flowRepository;
    private final FlowYamlUtils flowYamlUtils;

    private final ExperimentProcessService experimentProcessService;
    private final FileProcessService fileProcessService;
    private final FlowInstanceRepository flowInstanceRepository;
    private final SnippetCache snippetCache;

    public FlowService(Globals globals, FlowRepository flowRepository, FlowYamlUtils flowYamlUtils, ExperimentProcessService experimentProcessService, FileProcessService fileProcessService, FlowInstanceRepository flowInstanceRepository, SnippetCache snippetCache) {
        this.globals = globals;
        this.flowRepository = flowRepository;
        this.flowYamlUtils = flowYamlUtils;
        this.experimentProcessService = experimentProcessService;
        this.fileProcessService = fileProcessService;
        this.flowInstanceRepository = flowInstanceRepository;
        this.snippetCache = snippetCache;
    }

    public enum TaskProducingStatus { OK, VERIFY_ERROR, PRODUCING_ERROR }
    public enum FlowVerifyStatus { OK,
        NOT_VERIFIED_YET_ERROR,
        FLOW_CODE_EMPTY_ERROR,
        NO_INPUT_POOL_CODE_ERROR,
        NO_ANY_PROCESSES_ERROR,
        INPUT_TYPE_EMPTY_ERROR,
        NOT_ENOUGH_FOR_PARALLEL_EXEC_ERROR,
        SNIPPET_NOT_DEFINED_ERROR,
        FLOW_PARAMS_EMPTY_ERROR,
        SNIPPET_ALREADY_PROVIDED_BY_EXPERIMENT_ERROR,
        PROCESS_CODE_NOT_FOUND_ERROR,
        TOO_MANY_SNIPPET_CODES_ERROR,
        INPUT_CODE_NOT_SPECIFIED_ERROR,
        SNIPPET_NOT_FOUND_ERROR,
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

    public TaskProducingResult createTasks(Flow flow, String startWithResourcePoolCode) {
        TaskProducingResult result = new TaskProducingResult();
        result.flowVerifyStatus = verify(flow);
        if (result.flowVerifyStatus != FlowVerifyStatus.OK) {
            return result;
        }
        produce(result, flow, startWithResourcePoolCode);

        return result;
    }

    public FlowVerifyStatus verify(Flow flow) {
        if (flow==null) {
            return FlowVerifyStatus.NO_ANY_PROCESSES_ERROR;
        }
        if (StringUtils.isBlank(flow.code)) {
            return FlowVerifyStatus.FLOW_CODE_EMPTY_ERROR;
        }
        if (StringUtils.isBlank(flow.params)) {
            return FlowVerifyStatus.FLOW_PARAMS_EMPTY_ERROR;
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
            if (process.type == Enums.ProcessType.EXPERIMENT) {
                if (process.snippetCodes!=null && process.snippetCodes.size() > 0) {
                    return FlowVerifyStatus.SNIPPET_ALREADY_PROVIDED_BY_EXPERIMENT_ERROR;
                }
                if (StringUtils.isBlank(process.code)) {
                    return FlowVerifyStatus.SNIPPET_NOT_DEFINED_ERROR;
                }
            }
            else {
                if (process.getSnippetCodes() == null || process.getSnippetCodes().isEmpty()) {
                    return FlowVerifyStatus.SNIPPET_NOT_DEFINED_ERROR;
                }
                for (String snippetCode : process.snippetCodes) {
                    SnippetVersion sv = SnippetVersion.from(snippetCode);
                    Snippet snippet = snippetCache.findByNameAndSnippetVersion(sv.name, sv.version);
                    if (snippet==null) {
                        log.warn("Snippet wasn't found for code: {}, process: {}", snippetCode, process);
                        return FlowVerifyStatus.SNIPPET_NOT_FOUND_ERROR;
                    }
                }
            }
            if (process.parallelExec && (process.snippetCodes==null || process.snippetCodes.size()<2)) {
                return FlowVerifyStatus.NOT_ENOUGH_FOR_PARALLEL_EXEC_ERROR;
            }
            if (process.type== Enums.ProcessType.FILE_PROCESSING) {
                if (!process.parallelExec && process.snippetCodes.size()>1) {
                    return FlowVerifyStatus.TOO_MANY_SNIPPET_CODES_ERROR;
                }
            }
        }
        return FlowVerifyStatus.OK;
    }

    private void produce(TaskProducingResult result, Flow flow, String startWithResourcePoolCode) {

        FlowInstance fi = new FlowInstance();
        fi.setFlowId(flow.getId());
        fi.setCompleted(false);
        fi.setCreatedOn( System.currentTimeMillis() );
        fi.setInputResourcePoolCode(startWithResourcePoolCode);

        flowInstanceRepository.save(fi);

        result.flowYaml = flowYamlUtils.toFlowYaml(flow.getParams());
        int idx = 0;
        String inputResourcePoolCode = startWithResourcePoolCode;
        for (Process process : result.flowYaml.getProcesses()) {
            ++idx;

            String outputResourcePoolCode = getResourcePoolCode(flow.code, flow.getId(), process.code, idx);

            switch(process.type) {
                case FILE_PROCESSING:
                    fileProcessService.produceTasks(flow, process, idx, inputResourcePoolCode, outputResourcePoolCode);
                    break;
                case EXPERIMENT:
                    experimentProcessService.produceTasks(flow, process, idx, inputResourcePoolCode, outputResourcePoolCode);
                    break;
                default:
                    throw new IllegalStateException("Unknown process type");
            }
            inputResourcePoolCode = outputResourcePoolCode;
        }
    }

    private String getResourcePoolCode(String flowCode, long flowId, String processCode, int idx) {
        return String.format("%s-%d-%d-%s", flowCode, flowId, idx, processCode);
    }

}
