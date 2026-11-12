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

package ai.metaheuristic.ai.dispatcher.source_code;

import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Service for managing latch field on MH_SOURCE_CODE.
 *
 * Latch format: code:latch_count[;code:latch_count] with 0..N pairs.
 * Empty string means no latches.
 *
 * <p>Non-transactional: every call takes {@link SourceCodeSyncService}'s write lock of the SourceCode and runs the
 * write transaction of {@link SourceCodeLatchTxService} inside it, so parallel latch writers on one SourceCode are
 * serialized instead of racing on the row's @Version. Must be called with no transaction open.
 *
 * @author Sergio Lissner
 * Date: 2/28/2026
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class SourceCodeLatchService {

    private final SourceCodeLatchTxService sourceCodeLatchTxService;

    /**
     * Increase latch count for a specific code on a SourceCode.
     */
    public void increaseLatch(Long sourceCodeId, String code) {
        SourceCodeSyncService.getWithSyncVoid(sourceCodeId, () -> sourceCodeLatchTxService.increaseLatch(sourceCodeId, code));
    }

    /**
     * Decrease latch count for a specific code on a SourceCode.
     */
    public void decreaseLatch(Long sourceCodeId, String code) {
        SourceCodeSyncService.getWithSyncVoid(sourceCodeId, () -> sourceCodeLatchTxService.decreaseLatch(sourceCodeId, code));
    }

    /**
     * Reset (remove) latch for a specific code on a SourceCode.
     */
    public void resetLatch(Long sourceCodeId, String code) {
        SourceCodeSyncService.getWithSyncVoid(sourceCodeId, () -> sourceCodeLatchTxService.resetLatch(sourceCodeId, code));
    }
}
