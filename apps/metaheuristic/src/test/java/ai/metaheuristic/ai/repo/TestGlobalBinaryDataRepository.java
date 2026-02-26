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

package ai.metaheuristic.ai.repo;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.GlobalVariable;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableTxService;
import ai.metaheuristic.commons.spi.DispatcherBlobStorage;
import ai.metaheuristic.commons.spi.GeneralBlobTxService;
import ch.qos.logback.classic.LoggerContext;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureCache
class TestGlobalBinaryDataRepository {

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

    @Autowired private GlobalVariableTxService globalVariableService;
    @Autowired private GeneralBlobTxService variableBlobTxService;
    @Autowired private DispatcherBlobStorage dispatcherBlobStorage;
    @Autowired private GlobalVariableRepository globalVariableRepository;

    private @Nullable Long globalVariableId = null;

    @AfterEach
    public void after() {
        if (globalVariableId !=null) {
            globalVariableService.deleteById(globalVariableId);
        }
    }

    @Test
    public void test() throws InterruptedException, SQLException, IOException {
        final String s = "this is very short data";
        byte[] bytes = s.getBytes();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);


        globalVariableId = variableBlobTxService.createEmptyGlobalVariable("test-01", "test-file.bin");
        assertNotNull(globalVariableId);

        dispatcherBlobStorage.storeGlobalVariableData(globalVariableId, inputStream, bytes.length);
        GlobalVariable gv = globalVariableRepository.findById(globalVariableId).orElseThrow();


        dispatcherBlobStorage.accessGlobalVariableData(globalVariableId, (o)->{
                    try {
                        String actual = IOUtils.toString(o, StandardCharsets.UTF_8);
                        assertEquals(s, actual);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        // to check timestamp
        Thread.sleep(1100);

        final String s1 = "another one very short data";
        bytes = s1.getBytes();
        inputStream = new ByteArrayInputStream(bytes);
        dispatcherBlobStorage.storeGlobalVariableData(globalVariableId, inputStream, bytes.length);

        GlobalVariable gv1 = globalVariableRepository.findById(globalVariableId).orElseThrow();

        assertNotEquals(gv.uploadTs, gv1.uploadTs);
        dispatcherBlobStorage.accessGlobalVariableData(globalVariableId, (o)->{
            try {
                String actual = IOUtils.toString(o, StandardCharsets.UTF_8);
                assertEquals(s1, actual);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
