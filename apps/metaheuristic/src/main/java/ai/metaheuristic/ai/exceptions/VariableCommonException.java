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

package ai.metaheuristic.ai.exceptions;

import org.springframework.lang.Nullable;

/**
 * @author Serge
 * Date: 4/23/2020
 * Time: 6:23 PM
 */
public class VariableCommonException extends CommonErrorWithDataException {

    @Nullable
    public Long variableId;

    public VariableCommonException(String message, @Nullable Long variableId) {
        super(message);
        this.variableId = variableId;
    }

    @Override
    public String getAdditionalInfo() {
        return "VariableId: " + variableId;
    }
}
