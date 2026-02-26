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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.api.EnumsApi;
import ch.qos.logback.classic.LoggerContext;
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
import java.nio.file.Path;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureCache
public class TestBinaryDataRepository {

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

    @Autowired private VariableTxService variableTxService;
    @Autowired private VariableRepository variableRepository;

    private @Nullable Variable var1 = null;

    @AfterEach
    public void after() {
        if (var1!=null) {
            variableRepository.deleteById(var1.id);
        }
    }

    @Test
    public void test() throws InterruptedException {
        final byte[] bytes = "this is very short data".getBytes();

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);

        var1 = variableTxService.createInitializedTx(
                        inputStream, bytes.length, "test-01", "test-file.bin", 10L, Consts.TOP_LEVEL_CONTEXT_ID, EnumsApi.VariableType.binary);

        Timestamp ts = var1.getUploadTs();

        final Variable var2 = variableTxService.getVariable(var1.getId());
        assertNotNull(var2);
        final byte[] bytesVar2 = variableTxService.getVariableAsBytes(var1.getId());
        assertArrayEquals(bytes, bytesVar2);

        // to check timestamp
        Thread.sleep(1100);

        final byte[] bytes2 = "another one very short data".getBytes();
        final ByteArrayInputStream inputStream2 = new ByteArrayInputStream(bytes2);
        ExecContextSyncService.getWithSyncVoid(10L,
                ()-> VariableSyncService.getWithSyncVoidForCreation(var2.id,
                        ()-> variableTxService.updateWithTx(null, inputStream2, bytes2.length, var2.id)));

        final Variable var3 = variableRepository.findById(var2.getId()).orElse(null);
        assertNotNull(var3);
        assertNotEquals(ts, var3.getUploadTs());

        final byte[] bytesVar3 = variableTxService.getVariableAsBytes(var1.getId());
        assertArrayEquals(bytes2, bytesVar3);
    }
}
