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
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AssetFileService.
 *
 * AssetFileService is the service responsible for invalidating on-disk asset files
 * that SouthbridgeService caches in {@code globals.dispatcherTempPath}. It wraps
 * {@link VariableTxService#resetVariable(Long, Long)} so that the on-disk asset file
 * is removed BEFORE the DB update — this way:
 *   - a concurrent Southbridge delivery after the reset cannot serve the stale file,
 *   - if the DB update fails, the file is still gone and will be repopulated from
 *     whatever state the DB holds on the next delivery (self-healing).
 *
 * Test approach: NO Mockito. We hand-roll a {@link RecordingVariableTxService}
 * fake by subclassing {@link VariableTxService} (passing nulls for all 12 collaborator
 * constructor args — that's fine because the fake overrides the only method
 * AssetFileService calls). The fake captures the file-existence state at the moment
 * resetVariable runs, which is what proves the delete-before-delegate ordering
 * without needing any framework cooperation.
 *
 * TX requirement: {@link AssetFileService#resetVariable(Long, Long)} calls
 * {@link ai.metaheuristic.ai.utils.TxUtils#checkTxExists()} which consults Spring's
 * {@link TransactionSynchronizationManager}. We activate a synthetic TX flag for
 * the duration of each test and clear it in finally — no Spring context required.
 *
 * @author Sergio Lissner
 * Date: 4/7/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class TestAssetFileService {

    /**
     * Hand-rolled fake. Subclasses {@link VariableTxService} and overrides only
     * {@code resetVariable(Long, Long)} — the single method {@link AssetFileService}
     * delegates to. All other inherited methods would NPE on null collaborators if
     * touched; this is the desired behavior — any unexpected call from production
     * code under test would surface as an NPE here, which is a stronger
     * "no-other-interactions" signal than Mockito's verifyNoMoreInteractions.
     *
     * Captures (execContextId, variableId, file-existence-at-call-time, invocation
     * count). The file-existence capture is what proves delete-before-delegate
     * ordering: if AssetFileService correctly deletes the file first, then by the
     * time this override runs the file is already gone.
     */
    private static final class RecordingVariableTxService extends VariableTxService {

        final AtomicInteger invocations = new AtomicInteger();
        volatile Long capturedExecContextId;
        volatile Long capturedVariableId;
        volatile Boolean fileExistedAtCallTime;
        private final Path observedFile;

        RecordingVariableTxService(Path observedFile) {
            // All 12 collaborators are null; safe because the only overridden method
            // never touches them, and any unstubbed call would NPE — see class javadoc.
            super(null, null, null, null, null, null, null, null, null, null, null, null);
            this.observedFile = observedFile;
        }

        @Override
        public void resetVariable(Long execContextId, Long variableId) {
            this.capturedExecContextId = execContextId;
            this.capturedVariableId = variableId;
            this.fileExistedAtCallTime = Files.exists(observedFile);
            invocations.incrementAndGet();
            // do not delegate to super — super would NPE on null collaborators.
        }
    }

    /** Activate a synthetic Spring TX so {@code TxUtils.checkTxExists()} passes. */
    private static void withActiveTx(Runnable body) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            body.run();
        }
        finally {
            // Clear all per-thread Spring TX state — paranoid cleanup so a flaky
            // shutdown can't leak into a sibling test running on the same thread.
            TransactionSynchronizationManager.clear();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    @SneakyThrows
    public void test_resetVariable_deletesAssetFileAndDelegatesToVariableTxService(@TempDir Path tempDir) {
        final long execContextId = 777L;
        final long variableId = 1241L;

        // Arrange: dispatcherTempPath with an existing stale asset file for variableId
        Path dispatcherTempPath = tempDir.resolve("dispatcher-temp");
        Path variableDir = dispatcherTempPath.resolve("variable");
        Files.createDirectories(variableDir);
        Path staleAssetFile = variableDir.resolve("variable-" + variableId);
        Files.writeString(staleAssetFile, "STALE-CONTENT-FROM-PREVIOUS-RUN", StandardCharsets.UTF_8);
        assertTrue(Files.exists(staleAssetFile));

        Globals globals = new Globals();
        globals.dispatcherTempPath = dispatcherTempPath;

        RecordingVariableTxService variableTxService = new RecordingVariableTxService(staleAssetFile);
        AssetFileService assetFileService = new AssetFileService(globals, variableTxService);

        // Act
        withActiveTx(() -> assetFileService.resetVariable(execContextId, variableId));

        // Assert: file is gone after the whole operation
        assertFalse(Files.exists(staleAssetFile),
                "AssetFileService.resetVariable must delete the stale asset file on disk");

        // Assert: VariableTxService.resetVariable was called exactly once with the same arguments
        assertEquals(1, variableTxService.invocations.get(),
                "VariableTxService.resetVariable must be called exactly once");
        assertEquals(execContextId, variableTxService.capturedExecContextId);
        assertEquals(variableId, variableTxService.capturedVariableId);
    }

    @Test
    @SneakyThrows
    public void test_resetVariable_worksWhenAssetFileDoesNotExist(@TempDir Path tempDir) {
        final long execContextId = 777L;
        final long variableId = 9999L;

        // Arrange: dispatcherTempPath exists but NO asset file for this variable
        Path dispatcherTempPath = tempDir.resolve("dispatcher-temp");
        Files.createDirectories(dispatcherTempPath.resolve("variable"));
        Path missingAssetFile = dispatcherTempPath.resolve("variable").resolve("variable-" + variableId);
        assertFalse(Files.exists(missingAssetFile));

        Globals globals = new Globals();
        globals.dispatcherTempPath = dispatcherTempPath;

        RecordingVariableTxService variableTxService = new RecordingVariableTxService(missingAssetFile);
        AssetFileService assetFileService = new AssetFileService(globals, variableTxService);

        // Act — must not throw
        withActiveTx(() -> assetFileService.resetVariable(execContextId, variableId));

        // Assert: delegation still happens even if there was nothing to delete
        assertEquals(1, variableTxService.invocations.get(),
                "delegation must still happen when no file existed to delete");
        assertEquals(execContextId, variableTxService.capturedExecContextId);
        assertEquals(variableId, variableTxService.capturedVariableId);
    }

    @Test
    @SneakyThrows
    public void test_resetVariable_deletesFileBeforeDelegating(@TempDir Path tempDir) {
        final long execContextId = 777L;
        final long variableId = 4242L;

        Path dispatcherTempPath = tempDir.resolve("dispatcher-temp");
        Path variableDir = dispatcherTempPath.resolve("variable");
        Files.createDirectories(variableDir);
        Path staleAssetFile = variableDir.resolve("variable-" + variableId);
        Files.writeString(staleAssetFile, "STALE", StandardCharsets.UTF_8);

        Globals globals = new Globals();
        globals.dispatcherTempPath = dispatcherTempPath;

        // The fake captures Files.exists(staleAssetFile) at the exact moment its
        // resetVariable override runs. If AssetFileService has the right ordering
        // (delete file FIRST, then call variableTxService.resetVariable), the
        // captured value will be false — the file is already gone by then.
        RecordingVariableTxService variableTxService = new RecordingVariableTxService(staleAssetFile);
        AssetFileService assetFileService = new AssetFileService(globals, variableTxService);

        withActiveTx(() -> assetFileService.resetVariable(execContextId, variableId));

        assertEquals(1, variableTxService.invocations.get());
        assertNotNull(variableTxService.fileExistedAtCallTime,
                "fake must have been invoked, so fileExistedAtCallTime must be populated");
        assertFalse(variableTxService.fileExistedAtCallTime,
                "file must be deleted BEFORE variableTxService.resetVariable is called");
    }
}
