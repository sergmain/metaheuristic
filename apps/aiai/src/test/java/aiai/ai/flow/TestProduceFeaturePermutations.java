package aiai.ai.flow;

import aiai.ai.launchpad.beans.ExperimentFeature;
import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.repositories.ExperimentFeatureRepository;
import aiai.ai.preparing.PreparingExperiment;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
public class TestProduceFeaturePermutations extends PreparingExperiment {

    @Autowired
    public ExperimentService experimentService;

    @Autowired
    public ExperimentFeatureRepository experimentFeatureRepository;

    @After
    public void after() {
        try {
            experimentFeatureRepository.deleteByExperimentId(experiment.getId());
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    @Test
    public void testFeaturePermutation() {
        experimentService.produceFeaturePermutations(experiment, Arrays.asList("aaa", "bbb", "ccc"));
        List<ExperimentFeature> features = experimentFeatureRepository.findByExperimentId(experiment.getId());
        assertNotNull(features);
        assertEquals(7, features.size());

        int i=0;
    }
}
