/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.flow;

import aiai.api.v1.launchpad.Process;
import aiai.ai.launchpad.beans.ExperimentFeature;
import aiai.ai.preparing.PreparingFlow;
import aiai.ai.utils.holders.IntHolder;
import aiai.ai.yaml.flow.FlowYaml;
import aiai.api.v1.EnumsApi;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
            p.type = EnumsApi.ProcessType.FILE_PROCESSING;
            p.name = "assembly raw file";
            p.code = "assembly-raw-file";

            p.snippetCodes = Collections.singletonList("snippet-01:1.1");
            p.collectResources = true;
            p.outputType = "assembled-raw";

            flowYaml.processes.add(p);
        }
        {
            Process p = new Process();
            p.type = EnumsApi.ProcessType.FILE_PROCESSING;
            p.name = "dataset processing";
            p.code = "dataset-processing";

            p.snippetCodes = Collections.singletonList("snippet-02:1.1");
            p.collectResources = true;
            p.outputType = "dataset-processing";

            flowYaml.processes.add(p);
        }
        {
            Process p = new Process();
            p.type = EnumsApi.ProcessType.FILE_PROCESSING;
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
            p.type = EnumsApi.ProcessType.EXPERIMENT;
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

        //noinspection UnnecessaryLocalVariable
        String yaml = flowYamlUtils.toString(flowYaml);
        return yaml;
    }

    @Test
    public void testFeatures() {
        EnumsApi.FlowValidateStatus status = flowService.validate(flow);
        assertEquals(EnumsApi.FlowValidateStatus.OK, status);


        // produce artifacts - features, sequences,...
        long mills = System.currentTimeMillis();
        log.info("Start experimentService.produceFeaturePermutations()");
        //noinspection ArraysAsListWithZeroOrOneArgument
        experimentService.produceFeaturePermutations(true, experiment.getId(), Arrays.asList("aaa"), new IntHolder());
        log.info("experimentService.produceFeaturePermutations() was finished for {}", System.currentTimeMillis() - mills);

        mills = System.currentTimeMillis();
        log.info("Start experimentFeatureRepository.findByExperimentId()");
        List<ExperimentFeature> features = experimentFeatureRepository.findByExperimentId(experiment.getId());
        log.info("experimentFeatureRepository.findByExperimentId() was finished for {}", System.currentTimeMillis() - mills);

        assertNotNull(features);
        // TODO 777 - just random number. need to change value for working test
        assertEquals(
                "TODO 777 - just random number. need to change value for working test",
                777, features.size());

        mills = System.currentTimeMillis();
        log.info("Start experimentService.produceTasks()");
        // produce sequences
        List<String> codes = Arrays.asList("aaa", "bbb", "ccc");
        Process process = new Process();
        process.order=1;
        IntHolder intHolder = new IntHolder();
        // TODO need change empty HashMaps to actual HashMaps with values
        experimentService.produceTasks(true, flow, flowInstance, process, experiment, new HashMap<>(), new HashMap<>(), intHolder);
        log.info("experimentService.produceTasks() was finished for {}", System.currentTimeMillis() - mills);

        // some global final check
        assertEquals(777, experimentFeatureRepository.findByExperimentId(experiment.getId()).size());


    }
}
