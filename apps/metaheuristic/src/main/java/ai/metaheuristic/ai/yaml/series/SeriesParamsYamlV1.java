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

package ai.metaheuristic.ai.yaml.series;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.*;

/**
 * @author Serge
 * Date: 3/30/2021
 * Time: 1:46 PM
 */
@Data
public class SeriesParamsYamlV1 implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @NoArgsConstructor
    public static class MetricValuesV1 {
        // key - name of metric, value - value of metric
        public LinkedHashMap<String, BigDecimal> values = new LinkedHashMap<>();
    }

    public final Map<String, String> hyperParams = new HashMap<>();

    public EnumsApi.Fitting fitting;

    public final MetricValuesV1 metrics = new MetricValuesV1();

    public final List<String> variables = new ArrayList<>();

}
