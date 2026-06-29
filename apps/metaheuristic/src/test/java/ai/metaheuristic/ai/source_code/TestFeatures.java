/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
import ai.metaheuristic.api.EnumsApi;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.preparing.PreparingExperiment;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.ai.spi.MhSpi;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
@Slf4j
public class TestFeatures extends PreparingExperiment {

    @Autowired private PreparingSourceCodeService preparingSourceCodeService;

        public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @Test
    public void testFeatures() {
        createExperiment();

        long mills = System.currentTimeMillis();
        log.info("Start experimentService.produceFeaturePermutations()");

        preparingSourceCodeService.produceTasksForTest(resolveSourceCode(getSourceCodeAndLang()), preparingSourceCodeData);
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
