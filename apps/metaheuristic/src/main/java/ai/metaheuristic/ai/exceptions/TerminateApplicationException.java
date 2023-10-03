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

package ai.metaheuristic.ai.exceptions;

/**
 * @author Sergio Lissner
 * Date: 6/19/2023
 * Time: 11:12 PM
 */
public class TerminateApplicationException extends RuntimeException {
    public TerminateApplicationException() {
    }

    public TerminateApplicationException(String message) {
        super(message);
    }

    public TerminateApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
