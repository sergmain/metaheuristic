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
package ai.metaheuristic.ai.core;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.utils.EnvProperty;

import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureCache
public class TestEnvProperty {

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

    @Value("#{ T(ai.metaheuristic.ai.utils.EnvProperty).minMax( environment.getProperty('mh.processor.download-dataset-task.timeout'), 1, 10, 5) }")
    private int envProperty;

    @Value("#{ T(ai.metaheuristic.ai.core.TestEnvProperty).getFile(environment.getProperty('mh.processor.download-dataset-task.timeout'), \"aaa.xml\" )}")
    private Path file1;

    @Value("#{ T(ai.metaheuristic.ai.core.TestEnvProperty).getFile(environment.getProperty('mh.processor.download-dataset-task.timeout'), \"pom.xml\" )}")
    private Path file2;

    public static Path getFile( String filename, String defFilename) {
        return Path.of(defFilename);
    }

    @Test
    public void testProp() {
        assertEquals(5, envProperty);
        assertEquals(Path.of("aaa.xml"), file1);
        assertEquals(Path.of("pom.xml"), file2);


        assertEquals(4, EnvProperty.minMax("4", 1, 5, 3));
        assertEquals(1, EnvProperty.minMax("-1", 1, 5, 3));
        assertEquals(1, EnvProperty.minMax("1", 1, 5,3 ));
        assertEquals(5, EnvProperty.minMax("5", 1, 5, 3));
        assertEquals(5, EnvProperty.minMax("9", 1, 5, 3));

        assertEquals(2, EnvProperty.minMax(" ", 1, 5, 2));

        assertThrows(IllegalStateException.class, ()->EnvProperty.minMax(" ", 1, 5, null));
        assertThrows(IllegalStateException.class, ()->EnvProperty.minMax(" ", 1, 5, 0));
        assertThrows(IllegalStateException.class, ()->EnvProperty.minMax(" ", 1, 5, 6));
        assertThrows(NumberFormatException.class, ()->EnvProperty.minMax("abc", 1, 5, 3));
    }
}
