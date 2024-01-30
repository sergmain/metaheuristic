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

package ai.metaheuristic.commons.exceptions;

/**
 * @author Serge
 * Date: 3/10/2020
 * Time: 2:56 AM
 */
public class BlankYamlParamsException extends RuntimeException {
    public BlankYamlParamsException() {
    }

    public BlankYamlParamsException(String message) {
        super(message);
    }

    public BlankYamlParamsException(String message, Throwable cause) {
        super(message, cause);
    }

    public BlankYamlParamsException(Throwable cause) {
        super(cause);
    }

    public BlankYamlParamsException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
