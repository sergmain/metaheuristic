package aiai.ai.flow;

import aiai.ai.launchpad.Process;
import aiai.ai.yaml.process.ProcessMetaYaml;
import aiai.ai.yaml.process.ProcessMetaYamlUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
public class TestProcess {

    @Autowired
    public ProcessMetaYamlUtils processMetaYamlUtils;

    @Test
    public void testProcessMeta() {
        Process p = new Process();

        p.meta = "metas:\n" +
                "- key: assembled-raw\n" +
                "  value: assembled-raw\n" +
                "- key: dataset\n" +
                "  value: dataset-processing\n" +
                "- key: feature\n" +
                "  value: feature";

        ProcessMetaYaml yaml = processMetaYamlUtils.toProcessYaml(p.meta);

        assertNotNull(yaml.get("dataset"));
        assertEquals("dataset-processing", yaml.get("dataset").getValue());

        assertNotNull(yaml.get("assembled-raw"));
        assertEquals("assembled-raw", yaml.get("assembled-raw").getValue());

        assertNotNull(yaml.get("feature"));
        assertEquals("feature", yaml.get("feature").getValue());
    }

}
