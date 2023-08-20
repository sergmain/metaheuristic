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

import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * @author Serge
 * Date: 11/16/2020
 * Time: 7:09 PM
 */
public class ExecContextTooManyInstancesException extends RuntimeException {

    public String sourceCodeUid;
    public int curr;
    public int max;

    public ExecContextTooManyInstancesException(String sourceCodeUid, int max, int curr) {
        this.sourceCodeUid = sourceCodeUid;
        this.curr = curr;
        this.max = max;
    }
}
