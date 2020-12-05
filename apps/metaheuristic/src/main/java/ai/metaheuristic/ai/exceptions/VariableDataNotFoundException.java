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

import ai.metaheuristic.api.EnumsApi;

/**
 * @author Serge
 * Date: 4/11/2020
 * Time: 5:14 PM
 */
public class VariableDataNotFoundException extends VariableCommonException {

    public Long variableId;
    public EnumsApi.VariableContext context;

    public VariableDataNotFoundException(Long variableId, EnumsApi.VariableContext context, String message) {
        super(message, variableId);
        this.variableId = variableId;
        this.context = context;
    }

    @Override
    public String getAdditionalInfo() {
        return "VariableId: " + variableId+", context: '" + context+"'";
    }
}
