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

package ai.metaheuristic.ai.yaml.reduce_values_function;

import ai.metaheuristic.ai.dispatcher.internal_functions.reduce_values.ReduceVariablesEnums;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 11/16/2021
 * Time: 8:47 PM
 */
@Data
@NoArgsConstructor
public class ReduceVariablesConfigParamsYaml implements BaseParams {

    public final int version=1;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reduce {
        public ReduceVariablesEnums.Policy policy;
        public int reducePercent;
        public String variable;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ByInstance {
        public String input;
        public String inputIs;
        public String outputIs;
    }

    @Data
    @NoArgsConstructor
    public static class Config {
        public List<Reduce> reduces;

        public boolean fixName;
        public String fittingVar;
        public String metricsVar;
        public String metricsName;
        public final Map<String, String> reduceByValue = new HashMap<>();
        public final List<ByInstance> reduceByInstance = new ArrayList<>();
    }

    public Config config;
}
