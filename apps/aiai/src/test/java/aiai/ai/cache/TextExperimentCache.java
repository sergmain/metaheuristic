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
