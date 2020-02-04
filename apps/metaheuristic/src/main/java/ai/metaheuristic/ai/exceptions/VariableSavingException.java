/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.exceptions;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Serge
 * Date: 5/3/2019
 * Time: 1:57 PM
 */
@Slf4j
public class VariableSavingException extends RuntimeException {

    public VariableSavingException(String message) {
        super(message);
        log.error(message);
    }

    public VariableSavingException(String message, Throwable cause) {
        super(message, cause);
        log.error(message);
    }
}
