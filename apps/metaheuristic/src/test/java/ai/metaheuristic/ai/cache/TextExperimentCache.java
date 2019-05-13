/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.ai.cache;

import ai.metaheuristic.ai.launchpad.beans.Experiment;
import ai.metaheuristic.ai.preparing.PreparingExperiment;
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

        assertNull(e1.getWorkbookId());
        assertNull(e2.getWorkbookId());

        assertEquals(e1.getWorkbookId(), e2.getWorkbookId());

        e2.setWorkbookId(1L);
        e2 = experimentCache.save(e2);

        e1 = experimentCache.findById(experiment.getId());
        e2 = experimentRepository.findById(experiment.getId()).orElse(null);

        assertNotNull(e1);
        assertNotNull(e2);

        assertNotNull(e1.getWorkbookId());
        assertNotNull(e2.getWorkbookId());

        assertEquals(e1.getWorkbookId(), e2.getWorkbookId());

        e2.setWorkbookId(null);
        e2 = experimentCache.save(e2);

        e1 = experimentCache.findById(experiment.getId());
        e2 = experimentRepository.findById(experiment.getId()).orElse(null);

        assertNotNull(e1);
        assertNotNull(e2);

        assertNull(e1.getWorkbookId());
        assertNull(e2.getWorkbookId());

        assertEquals(e1.getWorkbookId(), e2.getWorkbookId());

    }
}
