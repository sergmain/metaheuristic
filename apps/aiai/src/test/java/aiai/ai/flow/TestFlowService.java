package aiai.ai.flow;

import aiai.ai.Enums;
import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.preparing.PreparingFlow;
import aiai.ai.yaml.flow.FlowYaml;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
public class TestFlowService extends PreparingFlow {

    @Override
    public String getFlowParamsAsYaml() {
        FlowYaml flowYaml = new FlowYaml();
        {
            Process p = new Process();
            p.type = Enums.ProcessType.FILE_PROCESSING;
            p.name = "assembly raw file";
            p.code = "assembly-raw-file";

            p.inputType = "raw-part-data";
            p.snippetCodes = Collections.singletonList("snippet-01:1.1");
            p.collectResources = true;
            p.outputType = "assembled-raw";

            flowYaml.processes.add(p);
        }
        {
            Process p = new Process();
            p.type = Enums.ProcessType.FILE_PROCESSING;
            p.name = "dataset processing";
            p.code = "dataset-processing";

            p.snippetCodes = Collections.singletonList("snippet-02:1.1");
            p.collectResources = true;
            p.outputType = "dataset-processing";

            flowYaml.processes.add(p);
        }
        {
            Process p = new Process();
            p.type = Enums.ProcessType.FILE_PROCESSING;
            p.name = "feature processing";
            p.code = "feature-processing";

            p.snippetCodes = Arrays.asList("snippet-03:1.1", "snippet-04:1.1", "snippet-05:1.1");
            p.parallelExec = true;
            p.collectResources = true;
            p.outputType = "feature";

            flowYaml.processes.add(p);
        }
        {
            Process p = new Process();
            p.type = Enums.ProcessType.EXPERIMENT;
            p.name = "experiment";
            p.code = "test-experiment-code-01";

            p.metas.addAll(
                    Arrays.asList(
                            new Process.Meta("assembled-raw", "assembled-raw", null),
                            new Process.Meta("dataset", "dataset-processing", null),
                            new Process.Meta("feature", "feature", null)
                    )
            );

            flowYaml.processes.add(p);
        }

        String yaml = flowYamlUtils.toString(flowYaml);
        return yaml;
    }

    @Test
    public void testCreateTasks() {
        FlowService.FlowVerifyStatus status = flowService.verify(flow);
        assertEquals(FlowService.FlowVerifyStatus.OK, status);
        FlowService.TaskProducingResult result = flowService.createTasks(flow, "raw-parts-pool");
        List<Task> tasks = taskRepository.findByFlowInstanceId(result.flowInstance.getId());
        try {
            assertNotNull(result);
            assertNotNull(result.flowInstance);
            assertNotNull(tasks);
            assertFalse(tasks.isEmpty());
        } finally {
            taskRepository.deleteAll(tasks);
        }

    }
}
