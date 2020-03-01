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

package ai.metaheuristic.ai.source_code;

import ai.metaheuristic.ai.dispatcher.experiment.ExperimentService;
import ai.metaheuristic.ai.preparing.PreparingExperiment;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
public class TestProduceFeaturePermutations extends PreparingExperiment {

    @Autowired
    public ExperimentService experimentService;

    @Test
    public void testFeaturePermutation() {
        experimentService.produceFeaturePermutations(true, experiment, List.of("aaa", "bbb", "ccc"), new AtomicInteger());
        final ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();

        assertNotNull(epy.processing.features);
        assertEquals(7, epy.processing.features.size());

        int i=0;
    }
}
