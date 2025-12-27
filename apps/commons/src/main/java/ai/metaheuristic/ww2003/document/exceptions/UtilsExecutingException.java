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

package ai.metaheuristic.ww2003.document.exceptions;

import ai.metaheuristic.commons.S;

/**
 * @author Serge
 * Date: 4/20/2022
 * Time: 12:59 AM
 */
public class UtilsExecutingException extends RuntimeException {

    public UtilsExecutingException(String message) {
        super(message);
        if (S.b(message)) {
            throw new IllegalStateException("000.000 (S.b(message))");
        }
    }
}
