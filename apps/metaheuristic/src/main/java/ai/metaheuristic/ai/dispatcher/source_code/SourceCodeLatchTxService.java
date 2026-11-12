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
 * Write transactions of the latch field on MH_SOURCE_CODE, extracted from {@link SourceCodeLatchService}.
 *
 * <p>A latch change is a read-modify-write of a row that many callers share - every project of an RG pipeline writes the
 * latch of the pipeline's SourceCode. Each method therefore requires {@link SourceCodeSyncService}'s write lock of the
 * SourceCode to be held by the caller across the whole call, commit included, so the next writer reads what this one
 * committed. {@link SourceCodeLatchService} is the non-transactional entry point that takes the lock.
 *
 * <p>Error code prefix: {@code 01.566.}
 *
 * @author Sergio Lissner
 * Date: 11/12/2026
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class SourceCodeLatchTxService {

    private final SourceCodeCache sourceCodeCache;

    /**
     * Increase latch count for a specific code on a SourceCode.
     */
    @Transactional
    public void increaseLatch(Long sourceCodeId, String code) {
        SourceCodeSyncService.checkWriteLockPresent(sourceCodeId);
        SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode == null) {
            log.warn("01.566.060 SourceCode not found for id: {}, can't increase latch for code '{}'", sourceCodeId, code);
            return;
        }
        Map<String, Integer> map = SourceCodeUtils.parseLatch(sourceCode.latch);
        map.merge(code, 1, Integer::sum);
        sourceCode.latch = SourceCodeUtils.serializeLatch(map);
        sourceCodeCache.save(sourceCode);
    }

    /**
     * Decrease latch count for a specific code on a SourceCode.
     */
    @Transactional
    public void decreaseLatch(Long sourceCodeId, String code) {
        SourceCodeSyncService.checkWriteLockPresent(sourceCodeId);
        SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode == null) {
            log.warn("01.566.080 SourceCode not found for id: {}, can't decrease latch for code '{}'", sourceCodeId, code);
            return;
        }
        Map<String, Integer> map = SourceCodeUtils.parseLatch(sourceCode.latch);
        Integer current = map.get(code);
        if (current == null || current <= 0) {
            log.warn("01.566.100 Latch count for code '{}' is already 0 or absent on SourceCode #{}", code, sourceCodeId);
            return;
        }
        map.put(code, current - 1);
        sourceCode.latch = SourceCodeUtils.serializeLatch(map);
        sourceCodeCache.save(sourceCode);
    }

    /**
     * Reset (remove) latch for a specific code on a SourceCode.
     */
    @Transactional
    public void resetLatch(Long sourceCodeId, String code) {
        SourceCodeSyncService.checkWriteLockPresent(sourceCodeId);
        SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode == null) {
            log.warn("01.566.120 SourceCode not found for id: {}, can't reset latch for code '{}'", sourceCodeId, code);
            return;
        }
        Map<String, Integer> map = SourceCodeUtils.parseLatch(sourceCode.latch);
        map.remove(code);
        sourceCode.latch = SourceCodeUtils.serializeLatch(map);
        sourceCodeCache.save(sourceCode);
    }
}
