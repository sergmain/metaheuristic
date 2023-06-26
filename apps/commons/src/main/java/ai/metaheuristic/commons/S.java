/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.commons;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * @author Serge
 * Date: 5/3/2019
 * Time: 1:50 PM
 */
public final class S {

    public static @NonNull String f(@NonNull String format, @Nullable Object... args) {
        return Objects.requireNonNull(String.format(format, args));
    }

    public static @NonNull String f(@NonNull Locale l, @NonNull String format, Object... args) {
        return String.format(l, format, args);
    }

    public static boolean b(@Nullable String s) {
        return s==null || s.isBlank();
    }
}
