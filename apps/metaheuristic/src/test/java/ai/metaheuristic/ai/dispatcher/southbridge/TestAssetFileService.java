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
import org.mockito.InOrder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
 * @author Sergio Lissner
 * Date: 4/7/2026
 */
public class TestAssetFileService {

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

        VariableTxService variableTxService = mock(VariableTxService.class);

        AssetFileService assetFileService = new AssetFileService(globals, variableTxService);

        // Act
        assetFileService.resetVariable(execContextId, variableId);

        // Assert: file is gone
        assertFalse(Files.exists(staleAssetFile),
                "AssetFileService.resetVariable must delete the stale asset file on disk");

        // Assert: VariableTxService.resetVariable was called with the same arguments
        verify(variableTxService).resetVariable(execContextId, variableId);
        verifyNoMoreInteractions(variableTxService);
    }

    @Test
    @SneakyThrows
    public void test_resetVariable_worksWhenAssetFileDoesNotExist(@TempDir Path tempDir) {
        final long execContextId = 777L;
        final long variableId = 9999L;

        // Arrange: dispatcherTempPath exists but NO asset file for this variable
        Path dispatcherTempPath = tempDir.resolve("dispatcher-temp");
        Files.createDirectories(dispatcherTempPath.resolve("variable"));

        Globals globals = new Globals();
        globals.dispatcherTempPath = dispatcherTempPath;

        VariableTxService variableTxService = mock(VariableTxService.class);
        AssetFileService assetFileService = new AssetFileService(globals, variableTxService);

        // Act — must not throw
        assetFileService.resetVariable(execContextId, variableId);

        // Assert: delegation still happens even if there was nothing to delete
        verify(variableTxService).resetVariable(execContextId, variableId);
        verifyNoMoreInteractions(variableTxService);
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

        VariableTxService variableTxService = mock(VariableTxService.class);

        // Record order: when variableTxService.resetVariable is called, the file must already be gone.
        org.mockito.Mockito.doAnswer(inv -> {
            assertFalse(Files.exists(staleAssetFile),
                    "file must be deleted BEFORE variableTxService.resetVariable is called");
            return null;
        }).when(variableTxService).resetVariable(execContextId, variableId);

        AssetFileService assetFileService = new AssetFileService(globals, variableTxService);
        assetFileService.resetVariable(execContextId, variableId);

        // Redundant-but-explicit ordering verification with InOrder
        InOrder order = inOrder(variableTxService);
        order.verify(variableTxService).resetVariable(execContextId, variableId);
        order.verifyNoMoreInteractions();
    }
}
