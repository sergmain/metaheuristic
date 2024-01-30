/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
package ai.metaheuristic.ai.utils;

import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.io.File;
import java.nio.file.Path;

public class EnvProperty {

    public static File getFile(String filename ) {
        return new File(filename);
    }

    public static @Nullable String strIfNotBlankElseNull(@Nullable String prop) {
        if (StringUtils.isBlank(prop)) {
            return null;
        }
        return prop;
    }

    public static @Nullable Long longIfNotBlankElseNull(@Nullable String prop) {
        if (StringUtils.isBlank(prop)) {
            return null;
        }
        return Long.valueOf(prop);
    }

    public static int minMax(String prop, int min, int max, @Nullable Integer defForBlank) {
        if (StringUtils.isBlank(prop)) {
            if (defForBlank==null) {
                throw new IllegalStateException("prop and defForBlank both are null");
            }
            if (defForBlank>max || defForBlank<min) {
                throw new IllegalStateException("misconfiguration: min: " + min +", max: " + max +", def: " +defForBlank);
            }
            return defForBlank;
        }
        int i = Integer.parseInt(prop);
        int value = minMax(i, min, max);
        return value;
    }

    public static int minMax(int curr, int min, int max) {
        if (curr >=min && curr <=max) {
            return curr;
        }
        else if (curr <min) {
            return min;
        }
        return max;
    }

    public static long minMax(long curr, int min, int max) {
        if (curr >=min && curr <=max) {
            return curr;
        }
        else if (curr <min) {
            return min;
        }
        return max;
    }

    public static @Nullable Path toFile(@Nullable String dirAsString) {
        if (StringUtils.isBlank(dirAsString)) {
            return null;
        }

        // special case for ./some-dir
        if (dirAsString.charAt(0) == '.' && (dirAsString.charAt(1) == '\\' || dirAsString.charAt(1) == '/')) {
            return Path.of(dirAsString.substring(2));
        }
        return Path.of(dirAsString);
    }
}
