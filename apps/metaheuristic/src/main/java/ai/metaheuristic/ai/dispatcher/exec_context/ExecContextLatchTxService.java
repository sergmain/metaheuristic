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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Service for managing latch field on MH_EXEC_CONTEXT.
 *
 * Latch format: code:latch_count[;code:latch_count] with 0..N pairs.
 * Empty string means no latches.
 *
 * Mirrors {@link ai.metaheuristic.ai.dispatcher.source_code.SourceCodeLatchService}
 * and reuses {@link SourceCodeUtils#parseLatch(String)} / {@link SourceCodeUtils#serializeLatch(Map)}
 * since the latch string format is identical.
 *
 * @author Sergio Lissner
 * Date: 4/12/2026
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextLatchTxService {

    private final ExecContextCache execContextCache;

    /**
     * Increase latch count for a specific code on an ExecContext.
     */
    @Transactional
    public void increaseLatch(Long execContextId, String code) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            log.warn("569.060 ExecContext not found for id: {}, can't increase latch for code '{}'", execContextId, code);
            return;
        }
        Map<String, Integer> map = SourceCodeUtils.parseLatch(execContext.latch);
        map.merge(code, 1, Integer::sum);
        execContext.latch = SourceCodeUtils.serializeLatch(map);
        execContextCache.save(execContext);
    }

    /**
     * Decrease latch count for a specific code on an ExecContext.
     */
    @Transactional
    public void decreaseLatch(Long execContextId, String code) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            log.warn("569.080 ExecContext not found for id: {}, can't decrease latch for code '{}'", execContextId, code);
            return;
        }
        Map<String, Integer> map = SourceCodeUtils.parseLatch(execContext.latch);
        Integer current = map.get(code);
        if (current == null || current <= 0) {
            log.warn("569.100 Latch count for code '{}' is already 0 or absent on ExecContext #{}", code, execContextId);
            return;
        }
        map.put(code, current - 1);
        execContext.latch = SourceCodeUtils.serializeLatch(map);
        execContextCache.save(execContext);
    }

    /**
     * Reset (remove) latch for a specific code on an ExecContext.
     */
    @Transactional
    public void resetLatch(Long execContextId, String code) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            log.warn("569.120 ExecContext not found for id: {}, can't reset latch for code '{}'", execContextId, code);
            return;
        }
        Map<String, Integer> map = SourceCodeUtils.parseLatch(execContext.latch);
        map.remove(code);
        execContext.latch = SourceCodeUtils.serializeLatch(map);
        execContextCache.save(execContext);
    }
}
