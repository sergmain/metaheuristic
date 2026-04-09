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
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Owns invalidation of the on-disk variable asset cache that {@link SouthbridgeService}
 * populates in {@code globals.dispatcherTempPath/variable/variable-<id>}.
 *
 * Problem this solves:
 *   SouthbridgeService caches variable contents on disk, keyed by variable id only.
 *   On a repeat delivery, if {@code assetFile.isContent} is true, Southbridge streams
 *   the cached file WITHOUT consulting the DB. When a variable is reset (task reset
 *   during objective re-execution), the DB row is cleared but the disk file lingers
 *   — so the next {@code deliverVariable} call serves stale bytes to the processor.
 *
 * Contract of {@link #resetVariable(Long, Long)}:
 *   - Deletes the on-disk asset file FIRST, unconditionally. This happens inside the
 *     caller's transaction, before the DB reset. If the delete fails the method
 *     still proceeds (the file will be repopulated from the DB on the next delivery).
 *     If the DB update later fails and rolls back, the file is gone either way —
 *     the next {@code deliverVariable} call will repopulate it from whatever state
 *     the DB holds at that moment. Self-healing.
 *   - Then delegates to {@link VariableTxService#resetVariable(Long, Long)} to clear
 *     the DB row.
 *
 * This service is the ONLY place (outside VariableTxService itself) that should be
 * calling {@code VariableTxService.resetVariable}. All task-reset paths should go
 * through here so the cache invariant is preserved.
 *
 * @author Sergio Lissner
 * Date: 4/7/2026
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class AssetFileService {

    private final Globals globals;
    private final VariableTxService variableTxService;

    /**
     * Invalidate the on-disk asset cache for a variable and then reset the variable's
     * DB row. Called from task-reset paths that need to clear both the cache and the
     * DB-side state of an output variable.
     *
     * Order: filesystem delete first, then DB reset. See class javadoc for rationale.
     */
    public void resetVariable(Long execContextId, Long variableId) {
        TxUtils.checkTxExists();

        deleteAssetFile(variableId);
        variableTxService.resetVariable(execContextId, variableId);
    }

    /**
     * Delete the on-disk asset file for a variable, if present. Swallows IOException
     * with a warning — next {@code deliverVariable} will repopulate from the DB.
     *
     * Package-private so the characterization tests can exercise it directly.
     */
    void deleteAssetFile(Long variableId) {
        Path assetFile = resolveAssetFile(variableId);
        try {
            boolean deleted = Files.deleteIfExists(assetFile);
            if (deleted) {
                log.info("821.020 Deleted stale asset file {}", assetFile.toAbsolutePath());
            }
        }
        catch (IOException e) {
            // Do not propagate. A lingering stale file is worse than a log line,
            // but corrupting the caller's transaction because of a transient FS
            // error is worse still. The file will be overwritten on the next
            // deliverVariable call anyway (which calls storeToFileWithTx, which
            // uses Files.write with CREATE + TRUNCATE_EXISTING semantics).
            log.warn("821.040 Couldn't delete asset file {}: {}", assetFile.toAbsolutePath(), e.getMessage());
        }
    }

    /**
     * Resolve the on-disk path for a variable's asset file. Mirrors the path
     * computation in {@link SouthbridgeService#deliverVariable} via
     * {@code AssetUtils.prepareFileForVariable(dispatcherTempPath, "variable-<id>", ...)}.
     *
     * The path is {@code <dispatcherTempPath>/variable/variable-<variableId>}.
     */
    private Path resolveAssetFile(Long variableId) {
        return globals.dispatcherTempPath
                .resolve(EnumsApi.DataType.variable.toString())
                .resolve("" + EnumsApi.DataType.variable + '-' + variableId);
    }
}
