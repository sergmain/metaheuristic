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

package ai.metaheuristic.ai.yaml.series;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Serge
 * Date: 3/30/2021
 * Time: 1:46 PM
 */
@Data
public class SeriesParamsYaml implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @NoArgsConstructor
    public static class MetricValues {
        // key - name of metric, value - value of metric
        public final LinkedHashMap<String, BigDecimal> values = new LinkedHashMap<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ExperimentPart {
        public String taskContextId;
        public EnumsApi.Fitting fitting;
        public final Map<String, String> hyperParams = new HashMap<>();
        public final MetricValues metrics = new MetricValues();
        public final List<String> variables = new ArrayList<>();
    }

    // it will actually be handled as Set. it's List for a compatibility with yaml
    public final List<ExperimentPart> parts = new ArrayList<>();

    public final Map<EnumsApi.Fitting, Integer> fittingCounts = new HashMap<>();

    public final List<String> experimentResults = new ArrayList<>();

}
