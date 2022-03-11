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

package ai.metaheuristic.api.data.function;

import ai.metaheuristic.api.EnumsApi;
import org.springframework.lang.Nullable;

/**
 * @author Serge
 * Date: 3/12/2020
 * Time: 9:11 PM
 */
public interface SimpleFunctionDefinition {
    String getCode();
    @Nullable String getParams();
    EnumsApi.FunctionExecContext getContext();
    default EnumsApi.FunctionRefType getRefType() {
        return EnumsApi.FunctionRefType.code;
    }
}
