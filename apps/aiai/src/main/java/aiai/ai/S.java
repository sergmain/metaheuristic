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

package aiai.ai;

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
}
