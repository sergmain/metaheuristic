package aiai.ai.flow;

import aiai.ai.Enums;
import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.repositories.FlowRepository;
import aiai.ai.yaml.flow.FlowYaml;
import aiai.ai.yaml.flow.FlowYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("launchpad")
public class TestFlowService {

    @Autowired
    public FlowRepository flowRepository;

    @Autowired
    public FlowService flowService;

    @Autowired
    public FlowYamlUtils flowYamlUtils;

    private Flow flow=null;

    @Before
    public void init() {
        flow = new Flow();
        flow.setCode("test-flow-code");

        String params = getFlowParamsAsYaml();
        flow.setParams(params);

        flowRepository.save(flow);
    }

    private String getFlowParamsAsYaml() {
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
            p.code = "experiment-code-01";

            flowYaml.processes.add(p);
        }

        String yaml = flowYamlUtils.toString(flowYaml);
        return yaml;
    }

    @After
    public void finish() {
        if (flow!=null) {
            flowRepository.delete(flow);
        }
    }

    @Test
    public void testCreateTasks() {
        FlowService.FlowVerifyStatus status = flowService.verify(flow);
        assertEquals(FlowService.FlowVerifyStatus.OK, status);

    }
}
