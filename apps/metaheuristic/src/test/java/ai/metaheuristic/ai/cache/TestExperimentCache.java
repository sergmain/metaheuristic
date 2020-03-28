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

import ai.metaheuristic.ai.dispatcher.beans.Experiment;
import ai.metaheuristic.ai.preparing.PreparingCore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
public class TestExperimentCache extends PreparingCore {

    @Test
    public void testCache() {
        Experiment e1;
        Experiment e2;
        e1 = experimentCache.findById(experiment.getId());
        e2 = experimentRepository.findById(experiment.getId()).orElse(null);

        assertNotNull(e1);
        assertNotNull(e2);

        System.out.println("\n\ne1.params:\n" + e1.getParams());

        assertNull(e1.getExecContextId());
        assertNull(e2.getExecContextId());

        assertEquals(e1.getExecContextId(), e2.getExecContextId());

        e2.setExecContextId(1L);
        e2 = experimentCache.save(e2);

        e1 = experimentCache.findById(experiment.getId());
        e2 = experimentRepository.findById(experiment.getId()).orElse(null);

        assertNotNull(e1);
        assertNotNull(e2);

        assertNotNull(e1.getExecContextId());
        assertNotNull(e2.getExecContextId());

        assertEquals(e1.getExecContextId(), e2.getExecContextId());

        e2.setExecContextId(null);
        e2 = experimentCache.save(e2);

        e1 = experimentCache.findById(experiment.getId());
        e2 = experimentRepository.findById(experiment.getId()).orElse(null);

        assertNotNull(e1);
        assertNotNull(e2);

        assertNull(e1.getExecContextId());
        assertNull(e2.getExecContextId());

        assertEquals(e1.getExecContextId(), e2.getExecContextId());

    }
}
