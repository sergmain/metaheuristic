/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.utils;

import org.apache.commons.lang3.StringUtils;

import java.io.File;

public class EnvProperty {

    public static File getFile(String filename ) {
        return new File(filename);
    }

    public static String strIfBlankThenNull(String prop) {
        if (StringUtils.isBlank(prop)) {
            return null;
        }
        return prop;
    }

    public static int minMax(String prop, int min, int max, Integer defForBlank) {
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
        if (i>=min && i<=max) {
            return i;
        }
        else if (i<min) {
            return min;
        }
        return max;
    }

    public static File toFile(String dirAsString) {
        if (StringUtils.isBlank(dirAsString)) {
            return null;
        }

        // special case for ./some-dir
        if (dirAsString.charAt(0) == '.' && (dirAsString.charAt(1) == '\\' || dirAsString.charAt(1) == '/')) {
            return new File(dirAsString.substring(2));
        }
        return new File(dirAsString);
    }
}
