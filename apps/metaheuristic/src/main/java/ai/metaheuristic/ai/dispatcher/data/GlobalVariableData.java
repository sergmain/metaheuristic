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

package ai.metaheuristic.ai.dispatcher.data;

import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.ai.dispatcher.variable_global.SimpleGlobalVariable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Slice;

public class GlobalVariableData {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class GlobalVariablesResult extends BaseDataClass {
        public Slice<SimpleGlobalVariable> items;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class GlobalVariableResult extends BaseDataClass {
        public SimpleGlobalVariable data;

        public GlobalVariableResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public GlobalVariableResult(SimpleGlobalVariable data) {
            this.data = data;
        }
    }

}
