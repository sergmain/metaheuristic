/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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
 * Date: 5/7/2019
 * Time: 7:32 PM
 */
public class UnzipArchiveException extends RuntimeException {
    public UnzipArchiveException() {
    }

    public UnzipArchiveException(String message) {
        super(message);
    }

    public UnzipArchiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
