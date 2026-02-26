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

package ai.metaheuristic.ai.profiles;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.api.ConstsApi;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Path;
import java.util.List;

import static ai.metaheuristic.api.EnumsApi.DispatcherAssetMode.replicated;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@TestPropertySource(locations="classpath:test-dispatcher-profile.properties")
@AutoConfigureCache
public class TestDispatcherProfile {

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

    @Autowired private Globals globals;

    @Test
    public void simpleTest() {
//        assertEquals(12, globals.threadNumber.getScheduler());
        assertEquals(List.of("http://localhost", "https://127.0.0.1", "http://192.168.0.1"), globals.corsAllowedOrigins);

        assertTrue(globals.dispatcher.enabled);
        assertFalse(globals.security.sslRequired);
        assertEquals("qwe321", globals.dispatcher.masterUsername);
        assertEquals("123ewq", globals.dispatcher.masterPassword);
        assertSame(globals.function.securityCheck, Enums.FunctionSecurityCheck.always);
        assertNotNull(globals.dispatcherPath);
        assertEquals(Consts.DISPATCHER_DIR, globals.dispatcherPath.getFileName().toString());

        assertNotNull(globals.publicKeyStore.key);
        assertEquals(1, globals.publicKeyStore.key.length);
        assertEquals(Consts.DEFAULT_PUBLIC_KEY_CODE, globals.publicKeyStore.key[0].code);

        assertEquals(replicated, globals.dispatcher.asset.mode);
        assertEquals("http://localhost:33377", globals.dispatcher.asset.sourceUrl);
        assertEquals("1277", globals.dispatcher.asset.password);
        assertEquals("rest_user77", globals.dispatcher.asset.username);
        assertEquals(27, globals.dispatcher.asset.syncTimeout.toSeconds());
        assertEquals(913, globals.dispatcher.chunkSize.toMegabytes());

        assertEquals(12347, globals.dispatcher.timeout.gc.toSeconds());
        assertEquals(12345, globals.dispatcher.timeout.artifactCleaner.toSeconds());
        assertEquals(12343, globals.dispatcher.timeout.updateBatchStatuses.toSeconds());
        assertEquals(8, globals.dispatcher.timeout.batchDeletion.toDays());

        assertEquals(ConstsApi.SECONDS_300.toSeconds(), globals.dispatcher.timeout.getArtifactCleaner().toSeconds());
        assertEquals(ConstsApi.SECONDS_23.toSeconds(), globals.dispatcher.timeout.getUpdateBatchStatuses().toSeconds());
        assertEquals(ConstsApi.DAYS_14.toDays(), globals.dispatcher.timeout.getBatchDeletion().toDays());

    }
}
