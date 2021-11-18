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

package ai.metaheuristic.ai.dispatcher.data;

import ai.metaheuristic.ai.dispatcher.internal_functions.reduce_values.ReduceVariablesEnums;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.task_ml.metrics.MetricValues;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 11/13/2021
 * Time: 6:28 PM
 */
public class ReduceVariablesData {

    public static class Request {
        public final Map<String, Boolean> nullifiedVars = new HashMap<>();
    }

    public static class PermutedVariables {
        public final Map<String, String> values = new HashMap<>();
        @Nullable
        public EnumsApi.Fitting fitting;
        @Nullable
        public MetricValues metricValues;
    }

    public static class TopPermutedVariables {
        public final Map<String, String> values = new HashMap<>();
        public List<PermutedVariables> subPermutedVariables;
    }

    public static class VariablesData {
        public final List<TopPermutedVariables> permutedVariables = new ArrayList<>();
    }

    public static class ReduceVariablesResult {
        public final Map<String, String> byValue = new HashMap<>();
        public final Map<String, Boolean> byInstance = new HashMap<>();
    }
}
