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

import ai.metaheuristic.ai.preparing.PreparingExperiment;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("dispatcher")
public class TestFeatures extends PreparingExperiment {

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void testFeatures() {
        createExperiment();

        long mills = System.currentTimeMillis();
        log.info("Start experimentService.produceFeaturePermutations()");
        //noinspection unused
        SourceCodeApiData.TaskProducingResultComplex result = produceTasksForTest();
        log.info("experimentService.produceFeaturePermutations() was finished for {}", System.currentTimeMillis() - mills);

        mills = System.currentTimeMillis();
        log.info("Start experimentFeatureRepository.findByExperimentId()");
        final ExperimentParamsYaml epy = experiment.getExperimentParamsYaml();
        log.info("experimentFeatureRepository.findByExperimentId() was finished for {}", System.currentTimeMillis() - mills);

        String s = "feature-per-task";
        // todo 2020-03-12 right now permutation is being created dynamically at runtime.
        //  so for calculation an actual number of permutation we need to process all tasks in current SourceCode/ExecContext
/*
        assertNotNull(epy.processing.features);
        assertEquals(7, epy.processing.features.size());
*/
    }
}
