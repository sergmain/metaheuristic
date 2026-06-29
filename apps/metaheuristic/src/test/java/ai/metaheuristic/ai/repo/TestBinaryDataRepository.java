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
import ai.metaheuristic.ai.MhSharedItTest;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.spi.MhSpi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.CommonConsts;
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
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies two related contracts on {@link VariableTxService}:
 * <ol>
 *   <li>An inited variable round-trips its bytes through dispatcher blob
 *       storage — {@code createInitializedTx} + {@code getVariableAsBytes}.</li>
 *   <li>The immutability invariant is enforced — calling {@code updateWithTx}
 *       on an already-inited variable throws {@code IllegalStateException}
 *       with the {@code 171.100} marker, because {@code VariableTxService.update}
 *       refuses to mutate an inited row.</li>
 * </ol>
 * Before the immutability model, {@code update()} silently overwrote inited
 * variables and a single test exercised both "create then read" and
 * "create then mutate then read". The mutation half is now obsolete by
 * design; this test promotes that obsolescence into an explicit positive
 * assertion of the immutability rule.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class TestBinaryDataRepository extends MhSharedItTest {

    @Autowired private VariableTxService variableTxService;
    @Autowired private VariableRepository variableRepository;

    private @Nullable Variable var1 = null;

    @AfterEach
    public void after() {
        if (var1!=null) {
            variableRepository.deleteById(var1.id);
            var1 = null;
        }
    }

    @Test
    public void test_createAndReadInitedVariable_roundTripsBytes() {
        final byte[] bytes = "this is very short data".getBytes();
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);

        var1 = variableTxService.createInitializedTx(
                inputStream, bytes.length, "test-01", "test-file.bin", 10L, CommonConsts.TOP_LEVEL_CONTEXT_ID, EnumsApi.VariableType.binary);

        assertNotNull(var1);
        assertTrue(var1.inited, "Variable created via createInitializedTx must be inited");

        final Variable reloaded = variableTxService.getVariable(var1.getId());
        assertNotNull(reloaded);
        assertEquals(var1.id, reloaded.id);
        assertTrue(reloaded.inited);

        final byte[] readBack = variableTxService.getVariableAsBytes(var1.getId());
        assertArrayEquals(bytes, readBack, "bytes read back from dispatcher blob storage must match what was written");
    }

    @Test
    public void test_updatingInitedVariable_isRejectedByImmutabilityInvariant() {
        final byte[] originalBytes = "original payload".getBytes();
        final ByteArrayInputStream originalStream = new ByteArrayInputStream(originalBytes);

        var1 = variableTxService.createInitializedTx(
                originalStream, originalBytes.length, "test-02", "test-file.bin", 11L, CommonConsts.TOP_LEVEL_CONTEXT_ID, EnumsApi.VariableType.binary);
        assertTrue(var1.inited);

        final byte[] replacementBytes = "replacement payload — must be rejected".getBytes();
        final ByteArrayInputStream replacementStream = new ByteArrayInputStream(replacementBytes);
        final Long variableId = var1.getId();

        // updateWithTx on an inited variable must throw because
        // VariableTxService.update() enforces immutability via
        //     if (data.inited) throw new IllegalStateException("171.100 ...")
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                ExecContextSyncService.getWithSyncVoid(11L,
                        () -> VariableSyncService.getWithSyncVoidForCreation(variableId,
                                () -> variableTxService.updateWithTx(null, replacementStream, replacementBytes.length, variableId))));

        assertTrue(thrown.getMessage().contains("171.100"),
                "Exception must carry the 171.100 immutability marker, was: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("can't be mutated"),
                "Exception must say the variable can't be mutated, was: " + thrown.getMessage());

        // The original bytes must still be retrievable — the failed mutation
        // is a no-op from the caller's perspective.
        final byte[] readBack = variableTxService.getVariableAsBytes(variableId);
        assertArrayEquals(originalBytes, readBack, "Original bytes must survive the rejected update attempt");
    }
}
