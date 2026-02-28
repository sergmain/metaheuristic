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
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service for managing latch field on MH_SOURCE_CODE.
 *
 * Latch format: code:latch_count[;code:latch_count] with 0..N pairs.
 * Empty string means no latches.
 *
 * @author Sergio Lissner
 * Date: 2/28/2026
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class SourceCodeLatchService {

    private final SourceCodeCache sourceCodeCache;

    /**
     * Parse latch string into a map of code -> count.
     */
    public static Map<String, Integer> parseLatch(@Nullable String latch) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (latch == null || latch.isBlank()) {
            return result;
        }
        String[] pairs = latch.split(";");
        for (String pair : pairs) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colonIdx = trimmed.indexOf(':');
            if (colonIdx <= 0 || colonIdx == trimmed.length() - 1) {
                log.warn("566.020 Invalid latch pair format: '{}'", trimmed);
                continue;
            }
            String code = trimmed.substring(0, colonIdx);
            try {
                int count = Integer.parseInt(trimmed.substring(colonIdx + 1));
                result.put(code, count);
            } catch (NumberFormatException e) {
                log.warn("566.040 Invalid latch count in pair: '{}'", trimmed);
            }
        }
        return result;
    }

    /**
     * Serialize latch map back to string. Removes entries with count <= 0.
     */
    public static String serializeLatch(Map<String, Integer> latchMap) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : latchMap.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append(entry.getKey()).append(':').append(entry.getValue());
        }
        return sb.toString();
    }

    /**
     * Check if all latch counts are zero (or latch is empty).
     * Returns true if deletion is allowed.
     */
    public static boolean isDeleteAllowed(@Nullable String latch) {
        if (latch == null || latch.isBlank()) {
            return true;
        }
        Map<String, Integer> map = parseLatch(latch);
        return map.values().stream().allMatch(count -> count <= 0);
    }

    /**
     * Check if a specific latch code is present (count > 0) in the latch string.
     */
    public static boolean present(String code, @Nullable String latch) {
        if (latch == null || latch.isBlank()) {
            return false;
        }
        Map<String, Integer> map = parseLatch(latch);
        Integer count = map.get(code);
        return count != null && count > 0;
    }

    /**
     * Increase latch count for a specific code on a SourceCode.
     */
    @Transactional
    public void increaseLatch(Long sourceCodeId, String code) {
        SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode == null) {
            log.warn("566.060 SourceCode not found for id: {}, can't increase latch for code '{}'", sourceCodeId, code);
            return;
        }
        Map<String, Integer> map = parseLatch(sourceCode.latch);
        map.merge(code, 1, Integer::sum);
        sourceCode.latch = serializeLatch(map);
        sourceCodeCache.save(sourceCode);
    }

    /**
     * Decrease latch count for a specific code on a SourceCode.
     */
    @Transactional
    public void decreaseLatch(Long sourceCodeId, String code) {
        SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode == null) {
            log.warn("566.080 SourceCode not found for id: {}, can't decrease latch for code '{}'", sourceCodeId, code);
            return;
        }
        Map<String, Integer> map = parseLatch(sourceCode.latch);
        Integer current = map.get(code);
        if (current == null || current <= 0) {
            log.warn("566.100 Latch count for code '{}' is already 0 or absent on SourceCode #{}", code, sourceCodeId);
            return;
        }
        map.put(code, current - 1);
        sourceCode.latch = serializeLatch(map);
        sourceCodeCache.save(sourceCode);
    }

    /**
     * Reset (remove) latch for a specific code on a SourceCode.
     */
    @Transactional
    public void resetLatch(Long sourceCodeId, String code) {
        SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode == null) {
            log.warn("566.120 SourceCode not found for id: {}, can't reset latch for code '{}'", sourceCodeId, code);
            return;
        }
        Map<String, Integer> map = parseLatch(sourceCode.latch);
        map.remove(code);
        sourceCode.latch = serializeLatch(map);
        sourceCodeCache.save(sourceCode);
    }
}
