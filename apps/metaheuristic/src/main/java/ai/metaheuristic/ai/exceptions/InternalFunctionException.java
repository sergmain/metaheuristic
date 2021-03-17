/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import lombok.AllArgsConstructor;

/**
 * @author Serge
 * Date: 3/10/2021
 * Time: 1:54 AM
 */
public class InternalFunctionException extends RuntimeException {
    public final InternalFunctionData.InternalFunctionProcessingResult result;

    public InternalFunctionException(InternalFunctionData.InternalFunctionProcessingResult result) {
        this.result = result;
    }

}