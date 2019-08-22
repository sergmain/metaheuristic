/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai;

import java.util.Locale;

/**
 * @author Serge
 * Date: 5/3/2019
 * Time: 1:50 PM
 */
public final class S {

    public static String f(String format, Object... args) {
        return String.format(format, args);
    }

    public static String f(Locale l, String format, Object... args) {
        return String.format(l, format, args);
    }

    public static boolean b(String s) {
        return s==null || s.isBlank();
    }
}
