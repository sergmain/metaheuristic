/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.southbridge;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherCommandProcessor;
import ai.metaheuristic.ai.dispatcher.function.FunctionDataTxService;
import ai.metaheuristic.ai.dispatcher.keep_alive.KeepAliveService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTxService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableTxService;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.AbstractResource;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Characterization Test (Bug A): SouthbridgeService caches variable contents on disk
 * under {@code globals.dispatcherTempPath/variable/variable-<id>} and, on subsequent
 * calls, streams the cached file unconditionally if {@code assetFile.isContent} is true.
 * If the underlying variable has been updated since the first delivery (e.g. because a
 * task reset + re-execution re-wrote the variable blob), the processor receives the
 * OLD bytes from the disk cache. There is no cache-invalidation hook.
 *
 * Production scenario from ec=20:
 *   - First pipeline run ("replace") writes variable 1241 = "DR2-3" / "replace".
 *     Processor fetches variable 1241 via Southbridge, asset file gets populated with
 *     "DR2-3" / "replace" bytes.
 *   - User submits a "delete" objective. Reset runs, check-objectives-2 re-runs and
 *     correctly re-writes variable 1241 in the DB to "DR2-5" / "delete".
 *   - Task 1378 (the re-executed evaluate-objective-2) fetches variable 1241 via
 *     Southbridge. Asset file on disk still has the OLD bytes, Southbridge streams
 *     those unchanged. Task 1378 runs with stale inputs → produces the wrong amendment.
 *
 * This test exercises the extracted {@link SouthbridgeService#deliverVariable} method
 * directly (see the refactor that split deliverData into deliverFunction, deliverVariable,
 * deliverGlobalVariable and a shared streamAssetFile helper — same business logic, just
 * carved into testable pieces).
 *
 * CT workflow (Feathers):
 *   1. Green: the test asserts the CURRENT (buggy) behavior — after a simulated DB-side
 *      update, a second {@code deliverVariable} call still returns the ORIGINAL bytes
 *      from disk. Test passes today.
 *   2. Red: the assertion is flipped to describe the desired behavior — the second call
 *      returns the UPDATED bytes. Test fails.
 *   3. Green: a cache-invalidation hook is added so the stale disk file is removed
 *      when the variable blob is updated (or marked not-inited). Test passes again.
 *
 * @author Sergio Lissner
 * Date: 4/7/2026
 */
public class TestSouthbridgeDeliverVariableStaleCache {

    // An in-memory backing "DB" for variable content. The fake storeToFileWithTx
    // implementation below writes whatever this map currently says into the target file
    // — mirroring how the real VariableTxService.storeToFileWithTx reads from the DB
    // blob at the moment of the call.
    private final Map<Long, String> variableContentById = new HashMap<>();

    // Counts how many times the fake data saver was invoked. This is how we detect
    // whether the disk cache was consulted or bypassed on the second delivery.
    private final AtomicInteger dataSaverInvocations = new AtomicInteger(0);

    @Test
    @SneakyThrows
    public void test_CT_staleDiskCache_secondDeliveryServesOriginalBytes(@TempDir Path tempDir) {
        final long variableId = 1241L;
        final String ORIGINAL_CONTENT = "REPLACE-CONTENT-FROM-FIRST-RUN|DR2-3|replace";
        final String UPDATED_CONTENT = "DELETE-CONTENT-FROM-SECOND-RUN|DR2-5|delete";

        // === Arrange: wire a SouthbridgeService with a real filesystem and a fake VariableTxService ===
        Path dispatcherTempPath = tempDir.resolve("dispatcher-temp");
        Files.createDirectories(dispatcherTempPath);

        Globals globals = new Globals();
        globals.dispatcherTempPath = dispatcherTempPath;

        VariableTxService variableTxService = mock(VariableTxService.class);
        // The fake storeToFileWithTx writes whatever variableContentById currently holds.
        // This matches the real behavior: reading the DB blob at call time.
        doAnswer(inv -> {
            Long vid = inv.getArgument(0);
            Path trgFile = inv.getArgument(1);
            String content = variableContentById.get(vid);
            assertNotNull(content, "Fake DB has no content for variable #" + vid);
            Files.writeString(trgFile, content, StandardCharsets.UTF_8);
            dataSaverInvocations.incrementAndGet();
            return null;
        }).when(variableTxService).storeToFileWithTx(anyLong(), any(Path.class));

        // Every other SouthbridgeService dependency is irrelevant for deliverVariable —
        // pass mocks so the constructor is satisfied.
        SouthbridgeService southbridgeService = new SouthbridgeService(
                globals,
                variableTxService,
                mock(GlobalVariableTxService.class),
                mock(FunctionDataTxService.class),
                mock(DispatcherCommandProcessor.class),
                mock(KeepAliveService.class),
                mock(ApplicationEventPublisher.class),
                mock(ProcessorCache.class),
                mock(ProcessorTxService.class)
        );

        // === Phase 1: seed the "DB" with the ORIGINAL content (simulates after "replace" run) ===
        variableContentById.put(variableId, ORIGINAL_CONTENT);

        // === Phase 1b: first delivery — populates the disk cache ===
        CleanerInfo firstCall = southbridgeService.deliverVariable(
                /*taskId*/ null, Long.toString(variableId), /*chunkSize*/ null, /*chunkNum*/ 0);
        String firstDeliveredContent = drain(firstCall);
        assertEquals(ORIGINAL_CONTENT, firstDeliveredContent,
                "first delivery must return the original content");
        assertEquals(1, dataSaverInvocations.get(),
                "first delivery must have invoked the data saver exactly once (cache miss)");

        // Verify the asset file was created on disk at the expected path
        Path assetFile = dispatcherTempPath.resolve("variable").resolve("variable-" + variableId);
        assertTrue(Files.exists(assetFile), "asset file must exist on disk after first delivery");
        assertEquals(ORIGINAL_CONTENT, Files.readString(assetFile, StandardCharsets.UTF_8),
                "asset file on disk must contain the original content");

        // === Phase 2: simulate the DB-side update that happens during reset + re-execution ===
        // (In production: check-objectives-2 re-runs, setVariableAsNull nullifies the old
        // blob, then the same variable row is re-initialized with the "delete" content.)
        variableContentById.put(variableId, UPDATED_CONTENT);

        // === Phase 2b: second delivery — SHOULD return updated content, but currently does not ===
        CleanerInfo secondCall = southbridgeService.deliverVariable(
                /*taskId*/ null, Long.toString(variableId), /*chunkSize*/ null, /*chunkNum*/ 0);
        String secondDeliveredContent = drain(secondCall);

        // === Characterization assertions (Green = current buggy behavior) ===

        // (1) The data saver was NOT invoked a second time — cache hit.
        assertEquals(1, dataSaverInvocations.get(),
                "CHARACTERIZATION: data saver must NOT be invoked on second delivery "
                + "(current buggy behavior: the stale disk asset file is served as-is)");

        // (2) The returned bytes are the STALE original content.
        assertEquals(ORIGINAL_CONTENT, secondDeliveredContent,
                "CHARACTERIZATION: second delivery returns the STALE original content "
                + "from the disk cache, NOT the updated DB content. This is the smoking "
                + "gun: a processor refetching variable #" + variableId + " after reset "
                + "gets the pre-reset bytes.");

        // (3) The asset file on disk is still the original content.
        assertEquals(ORIGINAL_CONTENT, Files.readString(assetFile, StandardCharsets.UTF_8),
                "CHARACTERIZATION: asset file on disk still holds the original content — "
                + "nothing invalidated the cache when the DB-side variable was updated.");
    }

    /**
     * Companion contract test (stays Green throughout the fix).
     *
     * Documents the contract SouthbridgeService upholds and which the upcoming
     * AssetFileService will rely on: IF the asset file on disk is deleted between
     * two deliveries (simulating a cache invalidation), THEN the next deliverVariable
     * call re-invokes the data saver and returns the FRESH DB content.
     *
     * This is the positive half of the characterization: the cache exists, and
     * deleting the cache file is the correct invalidation primitive.
     */
    @Test
    @SneakyThrows
    public void test_contract_assetFileDeleted_secondDeliveryReReadsFromDb(@TempDir Path tempDir) {
        final long variableId = 2242L;
        final String ORIGINAL_CONTENT = "ORIGINAL-CONTENT";
        final String UPDATED_CONTENT = "UPDATED-CONTENT";

        // Arrange
        Path dispatcherTempPath = tempDir.resolve("dispatcher-temp");
        Files.createDirectories(dispatcherTempPath);

        Globals globals = new Globals();
        globals.dispatcherTempPath = dispatcherTempPath;

        VariableTxService variableTxService = mock(VariableTxService.class);
        doAnswer(inv -> {
            Long vid = inv.getArgument(0);
            Path trgFile = inv.getArgument(1);
            String content = variableContentById.get(vid);
            assertNotNull(content, "Fake DB has no content for variable #" + vid);
            Files.writeString(trgFile, content, StandardCharsets.UTF_8);
            dataSaverInvocations.incrementAndGet();
            return null;
        }).when(variableTxService).storeToFileWithTx(anyLong(), any(Path.class));

        SouthbridgeService southbridgeService = new SouthbridgeService(
                globals,
                variableTxService,
                mock(GlobalVariableTxService.class),
                mock(FunctionDataTxService.class),
                mock(DispatcherCommandProcessor.class),
                mock(KeepAliveService.class),
                mock(ApplicationEventPublisher.class),
                mock(ProcessorCache.class),
                mock(ProcessorTxService.class)
        );

        // Phase 1: seed DB and deliver — populates the disk cache
        variableContentById.put(variableId, ORIGINAL_CONTENT);
        String firstDelivered = drain(southbridgeService.deliverVariable(
                null, Long.toString(variableId), null, 0));
        assertEquals(ORIGINAL_CONTENT, firstDelivered);
        assertEquals(1, dataSaverInvocations.get());

        Path assetFile = dispatcherTempPath.resolve("variable").resolve("variable-" + variableId);
        assertTrue(Files.exists(assetFile));

        // Phase 2: DB update AND cache invalidation (delete the asset file)
        variableContentById.put(variableId, UPDATED_CONTENT);
        Files.deleteIfExists(assetFile);

        // Phase 3: second delivery — must re-invoke the data saver and return fresh content
        String secondDelivered = drain(southbridgeService.deliverVariable(
                null, Long.toString(variableId), null, 0));

        assertEquals(2, dataSaverInvocations.get(),
                "CONTRACT: data saver must be invoked again when the asset file is absent");
        assertEquals(UPDATED_CONTENT, secondDelivered,
                "CONTRACT: second delivery must return the UPDATED DB content "
                + "when the stale asset file has been deleted before the call");
        assertEquals(UPDATED_CONTENT, Files.readString(assetFile, StandardCharsets.UTF_8),
                "CONTRACT: asset file on disk now holds the updated content");
    }

    /** Drain the InputStream from a CleanerInfo into a UTF-8 String. */
    @SneakyThrows
    private static String drain(CleanerInfo cleanerInfo) {
        assertNotNull(cleanerInfo, "cleanerInfo must not be null");
        assertNotNull(cleanerInfo.entity, "cleanerInfo.entity must not be null");
        ResponseEntity<AbstractResource> entity = cleanerInfo.entity;
        AbstractResource body = entity.getBody();
        assertNotNull(body, "ResponseEntity body must not be null");
        try (InputStream is = body.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
