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

package ai.metaheuristic.ai.yaml.function_execution_time;

import ai.metaheuristic.ai.dispatcher.data.CacheData;
import ai.metaheuristic.ai.utils.JsonUtils;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 12/21/2019
 * Time: 11:38 PM
 */
@Data
public class FunctionExecutionTimeParamsYaml implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    public static class Key {
        public String functionCode;
        public String funcParams;
        public final Map<String, Map<String, String>> inline = new HashMap<>();
        public final List<CacheData.Sha256PlusLength> inputs = new ArrayList<>();

        public Key(String functionCode, String funcParams) {
            this.functionCode = functionCode;
            this.funcParams = funcParams;
        }

        @SneakyThrows
        public String asString() {
            return JsonUtils.getMapper().writeValueAsString(this);
        }
    }


    public final List<Integer> execTime = new ArrayList<>();
}
