/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Slf4j
@ActiveProfiles({"dispatcher", "mysql"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class TestFeatures extends PreparingExperiment {

    @Autowired private PreparingSourceCodeService preparingSourceCodeService;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void testFeatures() {
        createExperiment();

        long mills = System.currentTimeMillis();
        log.info("Start experimentService.produceFeaturePermutations()");

        preparingSourceCodeService.produceTasksForTest(getSourceCodeYamlAsString(), preparingSourceCodeData);
        log.info("experimentService.produceFeaturePermutations() was finished for {} milliseconds", System.currentTimeMillis() - mills);

        mills = System.currentTimeMillis();
        log.info("Start experimentFeatureRepository.findByExperimentId()");
        final ExperimentParamsYaml epy = getExperiment().getExperimentParamsYaml();
        log.info("experimentFeatureRepository.findByExperimentId() was finished for {} milliseconds", System.currentTimeMillis() - mills);

        String s = "feature-per-task";
        // todo 2020-03-12 right now permutation is being created dynamically at runtime.
        //  so for calculating an actual number of permutation we need to process all tasks in current SourceCode/ExecContext
/*
        assertNotNull(epy.processing.features);
        assertEquals(7, epy.processing.features.size());
*/
    }
}
