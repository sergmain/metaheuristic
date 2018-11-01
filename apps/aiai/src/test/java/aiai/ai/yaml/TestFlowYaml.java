package aiai.ai.yaml;

import aiai.ai.Enums;
import aiai.ai.yaml.flow.FlowYaml;
import aiai.ai.yaml.flow.FlowYamlUtils;
import aiai.ai.yaml.flow.Process;
import aiai.ai.yaml.sequence.SimpleResource;
import aiai.ai.yaml.sequence.SimpleSnippet;
import aiai.ai.yaml.sequence.TaskParamYaml;
import aiai.ai.yaml.sequence.TaskParamYamlUtils;
import aiai.apps.commons.yaml.snippet.SnippetType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestFlowYaml {

    @Autowired
    private FlowYamlUtils flowYamlUtils;

    @Test
    public void testYaml() {
        FlowYaml flow = new FlowYaml();

        Process p = new Process();
        p.id=1;
        p.name="dataset processing";
        p.returnAllResources = true;

        p.snippetCodes=Arrays.asList("snippet-01", "snippet-02");
        p.resourcePoolCode = "raw-part-data";
        p.type = Enums.ProcessType.FILE_PROCESSING;

        Process p1 = new Process();
        p1.id=1;
        p1.name="dataset processing";
        p1.returnAllResources = true;

        p1.snippetCodes=Arrays.asList("snippet-01", "snippet-02");
        p1.resourcePoolCode = "raw-part-data";
        p1.type = Enums.ProcessType.FILE_PROCESSING;

        flow.processes=Arrays.asList(p,p1);

        String s = flowYamlUtils.toString(flow);

        System.out.println(s);

//        TaskParamYaml seq1 = taskParamYamlUtils.toTaskYaml(s);
//        Assert.assertEquals(flow, seq1);
    }
}
