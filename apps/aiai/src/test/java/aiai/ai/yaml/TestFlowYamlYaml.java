package aiai.ai.yaml;

import aiai.ai.Enums;
import aiai.ai.yaml.flow.FlowYaml;
import aiai.ai.yaml.flow.FlowYamlUtils;
import aiai.ai.yaml.flow.Process;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestFlowYamlYaml {

    @Autowired
    private FlowYamlUtils flowYamlUtils;

    @Test
    public void testYaml() {
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
        // output resource code: flow-10-assembly-raw-file-snippet-01
        //
        // input resource:
        // - code: flow-10-assembly-raw-file-snippet-01
        //   type: assembled-raw
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
        // output resource code: flow-10-dataset-processing-snippet-02
        //
        // input resource:
        // - code: flow-10-assembly-raw-file-snippet-01
        //   type: assembled-raw
        // - code: flow-10-dataset-processing-snippet-02
        //   type: dataset-processing
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
        // output resource code: flow-10-feature-processing-snippet-03
        // output resource code: flow-10-feature-processing-snippet-04
        // output resource code: flow-10-feature-processing-snippet-05
        //
        // input resource:
        // - code: flow-10-assembly-raw-file-snippet-01
        //   type: assembled-raw
        // - code: flow-10-dataset-processing-snippet-02
        //   type: dataset-processing
        // - code: flow-10-feature-processing-snippet-03
        //   type: feature
        // - code: flow-10-feature-processing-snippet-04
        //   type: feature
        // - code: flow-10-feature-processing-snippet-05
        //   type: feature
        {
            Process p = new Process();
            p.type = Enums.ProcessType.EXPERIMENT;
            p.name = "experiment";
            p.code = "experiment-code-01";

            flowYaml.processes.add(p);
        }

        String yaml = flowYamlUtils.toString(flowYaml);
        System.out.println(yaml);

        FlowYaml f1 = flowYamlUtils.toFlowYaml(yaml);
        assertNotNull(f1);
        assertNotNull(f1.processes);
        assertFalse(f1.processes.isEmpty());
    }

    @Test
    public void testYaml_2() {
        FlowYaml flowYaml = new FlowYaml();


        Process p1 = new Process();
        p1.name="experiment";
        p1.collectResources = false;

        p1.inputType = "dataset";
        p1.type = Enums.ProcessType.EXPERIMENT;

        flowYaml.processes = Collections.singletonList(p1);

        String s = flowYamlUtils.toString(flowYaml);

        System.out.println(s);

//        TaskParamYaml seq1 = taskParamYamlUtils.toTaskYaml(s);
//        Assert.assertEquals(flow, seq1);
    }
}
