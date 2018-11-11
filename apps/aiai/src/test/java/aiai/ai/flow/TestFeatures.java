package aiai.ai.flow;

import aiai.ai.Enums;
import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.beans.ExperimentFeature;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.preparing.PreparingFlow;
import aiai.ai.yaml.flow.FlowYaml;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("launchpad")
public class TestFeatures extends PreparingFlow {

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
    public void testFeatures() {
        FlowService.FlowVerifyStatus status = flowService.verify(flow);
        assertEquals(FlowService.FlowVerifyStatus.OK, status);


        // produce artifacts - features, sequences,...
        long mills = System.currentTimeMillis();
        log.info("Start experimentService.produceFeaturePermutations()");
        Experiment experiment = new Experiment();
        experimentService.produceFeaturePermutations(experiment, null);
        log.info("experimentService.produceFeaturePermutations() was finished for {}", System.currentTimeMillis() - mills);

        mills = System.currentTimeMillis();
        log.info("Start experimentFeatureRepository.findByExperimentId()");
        List<ExperimentFeature> features = experimentFeatureRepository.findByExperimentId(experiment.getId());
        log.info("experimentFeatureRepository.findByExperimentId() was finished for {}", System.currentTimeMillis() - mills);

        assertNotNull(features);
        assertEquals(777, features.size());
        for (ExperimentFeature feature : features) {
            assertFalse(feature.isFinished);
        }

        mills = System.currentTimeMillis();
        log.info("Start experimentService.produceTasks()");
        // produce sequences
        List<String> codes = new ArrayList<>();
        experimentService.produceTasks(experiment, codes);
        log.info("experimentService.produceTasks() was finished for {}", System.currentTimeMillis() - mills);

        // some global final check
        assertEquals(777, experimentFeatureRepository.findByExperimentId(experiment.getId()).size());


    }
}
