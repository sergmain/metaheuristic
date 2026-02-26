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

package ai.metaheuristic.ai.experiment_result;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.experiment_result.ExperimentResultService;
import ai.metaheuristic.ai.preparing.PreparingExperiment;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.ai.yaml.experiment_result.ExperimentResultParamsJsonUtils;
import ai.metaheuristic.ai.yaml.experiment_result.ExperimentResultParamsYamlWithCache;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
@Disabled
@DisplayName("experiments postponed for very long period of time")
public class TestExperimentToJson extends PreparingExperiment {

    @org.junit.jupiter.api.io.TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbUrl = "jdbc:h2:file:" + tempDir.resolve("db-h2/mh").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE";
        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("mh.home", () -> tempDir.toAbsolutePath().toString());
        registry.add("spring.profiles.active", () -> "dispatcher,h2,test");
    }

    @BeforeAll
    static void setSystemProperties() {
        System.setProperty("mh.home", tempDir.toAbsolutePath().toString());
    }

    @AfterAll
    static void cleanupLogging() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();
    }

    @Autowired private ExperimentResultService experimentResultService;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void toExperimentStoredToExperimentResultToYaml() {
        createExperiment();

        preparingSourceCodeService.produceTasksForTest(getSourceCodeYamlAsString(), preparingSourceCodeData);

        assertNotNull(getExperiment());
        assertNotNull(getExperiment().getId());
//        assertNotNull(experiment.getExecContextId());

        ExperimentResultService.StoredToExperimentResultWithStatus r = ExperimentResultService.toExperimentStoredToExperimentResult(getExecContextForTest().asSimple(), getExperiment());
        assertEquals(Enums.StoringStatus.OK, r.status);

        String yaml = ExperimentResultParamsJsonUtils.BASE_UTILS.toString(r.experimentResultParamsYamlWithCache.experimentResult);

        System.out.println("yaml =\n" + yaml);
        ExperimentResultParamsYamlWithCache erpywc = new ExperimentResultParamsYamlWithCache(ExperimentResultParamsJsonUtils.BASE_UTILS.to(yaml));
        System.out.println("erpywc = " + erpywc);

        // TODO 2019-07-13 add here comparisons of ExperimentResultParamsYamlWithCache and erpywc
    }
}
