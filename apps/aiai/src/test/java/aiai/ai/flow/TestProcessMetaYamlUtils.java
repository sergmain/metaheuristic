package aiai.ai.flow;

import aiai.ai.yaml.process.ProcessMetaYaml;
import aiai.ai.yaml.process.ProcessMetaYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

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
        yaml

    }

}
