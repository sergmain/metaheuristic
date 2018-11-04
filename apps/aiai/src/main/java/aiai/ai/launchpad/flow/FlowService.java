package aiai.ai.launchpad.flow;

import aiai.ai.Globals;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.repositories.FlowRepository;
import aiai.ai.yaml.flow.FlowYaml;
import aiai.ai.yaml.flow.FlowYamlUtils;
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

    public FlowService(Globals globals, FlowRepository flowRepository, FlowYamlUtils flowYamlUtils) {
        this.globals = globals;
        this.flowRepository = flowRepository;
        this.flowYamlUtils = flowYamlUtils;
    }

    public enum TaskProducingStatus { OK, VERIFY_ERROR, PRODUCING_ERROR }
    public enum FlowStatus { OK,
        NO_ANY_PROCESSES_ERROR,
        FLOW_CODE_EMPTY_ERROR,
        NO_INPUT_TYPE_ERROR,

    }

    @Data
    @NoArgsConstructor
    public static class TaskProducingResult {
        public TaskProducingStatus status;
        public FlowStatus flowStatus;
        public List<Task> tasks = new ArrayList<>();

        public TaskProducingResult(TaskProducingStatus status) {
            this.status = status;
        }

        public TaskProducingResult(TaskProducingStatus verifyError, FlowStatus flowStatus) {
            status = verifyError;
            this.flowStatus = flowStatus;
        }
    }

    public TaskProducingResult createTasks(Flow flow) {
        FlowStatus flowStatus = verify(flow);
        if (flowStatus !=FlowStatus.OK) {
            return new TaskProducingResult(TaskProducingStatus.VERIFY_ERROR, flowStatus);;
        }
    }

    private FlowStatus verify(Flow flow) {
        if (flow==null) {
            return FlowStatus.NO_ANY_PROCESSES_ERROR;
        }
        if (StringUtils.isBlank(flow.code) || StringUtils.isBlank(flow.inputPoolCode) ) {
            return false;
        }
        FlowYaml flowYaml = flowYamlUtils.toFlowYaml(flow.params);
        if (flowYaml.getProcesses().isEmpty()) {
            return false;
        }
        if (StringUtils.isBlank(flowYaml.getProcesses().get(0).inputType)) {
            return false;
        }

    }
}
