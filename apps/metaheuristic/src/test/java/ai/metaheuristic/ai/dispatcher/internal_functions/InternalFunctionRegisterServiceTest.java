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

package ai.metaheuristic.ai.dispatcher.internal_functions;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.mhbp.data.ScenarioData;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Sergio Lissner
 * Date: 8/28/2023
 * Time: 1:52 PM
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureCache
public class InternalFunctionRegisterServiceTest {

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

    @Autowired
    private InternalFunctionRegisterService internalFunctionRegisterService;

    @Test
    public void test_cachable_functions() {
        assertTrue(internalFunctionRegisterService.getCachableFunctions().contains(Consts.MH_API_CALL_FUNCTION));
    }

    @Test
    public void test_initialization() {
        assertNotNull(internalFunctionRegisterService.get(Consts.MH_AGGREGATE_FUNCTION));
        assertNotNull(internalFunctionRegisterService.get(Consts.MH_ACCEPTANCE_TEST_FUNCTION));
        assertNotNull(internalFunctionRegisterService.get(Consts.MH_BATCH_LINE_SPLITTER_FUNCTION));
        assertNotNull(internalFunctionRegisterService.get(Consts.MH_ENHANCE_TEXT_FUNCTION));
    }

    @Test
    public void test_getScenarioCompatibleFunctions() {
        final List<ScenarioData.InternalFunction> functions = internalFunctionRegisterService.getScenarioCompatibleFunctions();
        assertNotNull(functions.stream().filter(f->f.getCode().equals(Consts.MH_AGGREGATE_FUNCTION)).findFirst().orElse(null));
        assertNotNull(functions.stream().filter(f->f.getCode().equals(Consts.MH_ACCEPTANCE_TEST_FUNCTION)).findFirst().orElse(null));
        assertNotNull(functions.stream().filter(f->f.getCode().equals(Consts.MH_BATCH_LINE_SPLITTER_FUNCTION)).findFirst().orElse(null));
        assertNotNull(functions.stream().filter(f->f.getCode().equals(Consts.MH_ENHANCE_TEXT_FUNCTION)).findFirst().orElse(null));
    }
}
