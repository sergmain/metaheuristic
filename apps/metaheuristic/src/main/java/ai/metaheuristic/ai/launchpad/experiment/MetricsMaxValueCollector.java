/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.experiment;

import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.yaml.metrics.MetricValues;
import ai.metaheuristic.ai.yaml.metrics.MetricsUtils;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Profile("launchpad")
@Slf4j
@RequiredArgsConstructor
public class MetricsMaxValueCollector {

    private final TaskRepository taskRepository;

    public double calcMaxValueForMetrics(ExperimentParamsYaml epy, Long featureId) {
        List<Long> ids = epy.getTaskFeatureIds(featureId);
        if (ids.isEmpty()) {
            return 0.0;
        }

        List<Object[]> list = taskRepository.findMetricsByIds(ids);
        //noinspection UnnecessaryLocalVariable
        double value = list.stream()
                .map(o -> {
                    MetricValues metricValues = MetricsUtils.getValues( MetricsUtils.to((String)o[1]) );
                    if (metricValues==null) {
                        return null;
                    }
                    String metricKey=null;
                    //noinspection LoopStatementThatDoesntLoop
                    for (Map.Entry<String, BigDecimal> entry : metricValues.values.entrySet()) {
                        metricKey = entry.getKey();
                        break;
                    }
                    if (metricKey==null) {
                        return null;
                    }
                    return metricValues.values.get(metricKey).doubleValue();
                }).filter(Objects::nonNull).max(Double::compareTo).orElse(0.0);
        return value;
    }
}
