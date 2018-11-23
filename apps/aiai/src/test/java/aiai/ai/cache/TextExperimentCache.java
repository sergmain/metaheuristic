package aiai.ai.cache;

import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.preparing.PreparingExperiment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
public class TextExperimentCache extends PreparingExperiment {

    @Test
    public void testCache() {
        Experiment e1;
        Experiment e2;
        e1 = experimentCache.findById(experiment.getId());
        e2 = experimentRepository.findById(experiment.getId()).orElse(null);

        assertNotNull(e1);
        assertNotNull(e2);

        assertNull(e1.getFlowInstanceId());
        assertNull(e2.getFlowInstanceId());

        assertEquals(e1.getFlowInstanceId(), e2.getFlowInstanceId());

        e2.setFlowInstanceId(1L);
        e2 = experimentCache.save(e2);

        e1 = experimentCache.findById(experiment.getId());
        e2 = experimentRepository.findById(experiment.getId()).orElse(null);

        assertNotNull(e1);
        assertNotNull(e2);

        assertNotNull(e1.getFlowInstanceId());
        assertNotNull(e2.getFlowInstanceId());

        assertEquals(e1.getFlowInstanceId(), e2.getFlowInstanceId());

        e2.setFlowInstanceId(null);
        e2 = experimentCache.save(e2);

        e1 = experimentCache.findById(experiment.getId());
        e2 = experimentRepository.findById(experiment.getId()).orElse(null);

        assertNotNull(e1);
        assertNotNull(e2);

        assertNull(e1.getFlowInstanceId());
        assertNull(e2.getFlowInstanceId());

        assertEquals(e1.getFlowInstanceId(), e2.getFlowInstanceId());

    }
}
