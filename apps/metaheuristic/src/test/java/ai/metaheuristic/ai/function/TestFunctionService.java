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

package ai.metaheuristic.ai.function;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.function.FunctionDataTxService;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.function.FunctionTxService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxTestingTopLevelService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ch.qos.logback.classic.LoggerContext;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.*;
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

import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 7/12/2019
 * Time: 5:42 PM
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureCache
@Slf4j
class TestFunctionService {

    public static final String TEST_FUNCTION = "test.function:1.0";
    public static final String FUNCTION_PARAMS = "AAA";

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

    @Autowired private FunctionTxService functionTxService;
    @Autowired private FunctionService functionTopLevelService;
    @Autowired private TxTestingTopLevelService txTestingTopLevelService;
    @Autowired private FunctionDataTxService functionDataService;
    @Autowired private Globals globals;

    public @Nullable Function function = null;

    @Test
    public void test() {
        ExecContextParamsYaml.FunctionDefinition sd = new ExecContextParamsYaml.FunctionDefinition();
        sd.code = TEST_FUNCTION;
        sd.params = null;
        TaskParamsYaml.FunctionConfig sc = functionTopLevelService.getFunctionConfig(sd);
        check(sc);

        ExecContextParamsYaml.FunctionDefinition sd1 = new ExecContextParamsYaml.FunctionDefinition();
        sd1.code = "test";
        sd1.params = null;
        sd1.refType = EnumsApi.FunctionRefType.type;
        TaskParamsYaml.FunctionConfig sc1 = functionTopLevelService.getFunctionConfig(sd1);
        check(sc1);
    }

    private static void check(TaskParamsYaml.@Nullable FunctionConfig sc) {
        assertNotNull(sc);
        assertNotNull(sc.params);
        final String[] split = StringUtils.split(sc.params);
        assertNotNull(split);
        assertEquals(1, split.length, "Expected: ["+ FUNCTION_PARAMS +"], actual: " + Arrays.toString(split));
        assertArrayEquals(new String[] {FUNCTION_PARAMS}, split);
    }

    @BeforeEach
    public void beforePreparingExperiment() {
        assertTrue(globals.testing);
        function = initFunction();
    }

    @SneakyThrows
    public Function initFunction() {
        final String testFunction = TEST_FUNCTION;
        final String file = "predict-filename.txt";
        final String test = "test";

        final Function f = txTestingTopLevelService.getOrCreateFunction(testFunction, test, file);
        return f;
    }

    @AfterEach
    public void afterPreparingExperiment() {
        long mills = System.currentTimeMillis();
        log.info("Start after()");
        if (function != null) {
            try {
                functionTxService.deleteFunction(function.getId(), function.code);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
            try {
                functionDataService.deleteByFunctionCode(function.getCode());
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        System.out.println("Was finished correctly");
        log.info("after() was finished for {} milliseconds", System.currentTimeMillis() - mills);
    }

}
