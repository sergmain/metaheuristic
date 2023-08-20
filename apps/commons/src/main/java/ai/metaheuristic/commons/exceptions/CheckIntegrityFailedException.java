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

package ai.metaheuristic.commons.exceptions;

/**
 * @author Serge
 * Date: 2/1/2020
 * Time: 10:42 PM
 */
public class CheckIntegrityFailedException extends RuntimeException{
    public CheckIntegrityFailedException() {
    }

    public CheckIntegrityFailedException(String message) {
        super(message);
    }
}
