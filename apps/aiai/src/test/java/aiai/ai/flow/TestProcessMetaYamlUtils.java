package aiai.ai.flow;

import aiai.ai.yaml.process.ProcessMetaYaml;
import aiai.ai.yaml.process.ProcessMetaYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.Collections;

@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("launchpad")
public class TestProcessMetaYamlUtils {

    @Autowired
    public ProcessMetaYamlUtils processMetaYamlUtils;

    private ProcessMetaYaml yaml;

    @Test
    public void testProcessMeta() {
        ProcessMetaYaml yaml = new ProcessMetaYaml();
        yaml.metas.addAll(
            Arrays.asList(
                new ProcessMetaYaml.ProcessMeta("type-1", "value-type-1", null),
                new ProcessMetaYaml.ProcessMeta("type-2", "value-type-2", null),
                new ProcessMetaYaml.ProcessMeta("type-3", "value-type-3", null)
            )
        );

        String s  = processMetaYamlUtils.toString(yaml);

        System.out.println(s);

        ProcessMetaYaml yaml1 = processMetaYamlUtils.toProcessYaml(s);

        Assert.assertEquals(yaml, yaml1);

        String s1 = "metas:\n" +
                "- key: type-1\n" +
                "  value: value-type-1\n" +
                "- key: type-2\n" +
                "  value: value-type-2\n" +
                "- key: type-3\n" +
                "  value: value-type-3";

        ProcessMetaYaml yaml2 = processMetaYamlUtils.toProcessYaml(s1);

        Assert.assertEquals(yaml, yaml2);
    }

}
