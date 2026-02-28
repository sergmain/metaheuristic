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

import ai.metaheuristic.api.EnumsApi;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SourceCodeUtils {

    private static final Pattern VARIABLE_NAME_CHARS_PATTERN = Pattern.compile("^[A-Za-z0-9_-][A-Za-z0-9._-]*$");

    public static EnumsApi.SourceCodeValidateStatus isVariableNameOk(String name) {
        Matcher m = VARIABLE_NAME_CHARS_PATTERN.matcher(name);
        return m.matches() ? EnumsApi.SourceCodeValidateStatus.OK : EnumsApi.SourceCodeValidateStatus.WRONG_FORMAT_OF_VARIABLE_NAME_ERROR;
    }


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
                log.warn("568.040 Invalid latch pair format: '{}'", trimmed);
                continue;
            }
            String code = trimmed.substring(0, colonIdx);
            try {
                int count = Integer.parseInt(trimmed.substring(colonIdx + 1));
                result.put(code, count);
            } catch (NumberFormatException e) {
                log.warn("568.080 Invalid latch count in pair: '{}'", trimmed);
            }
        }
        return result;
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
}
