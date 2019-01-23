/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.experiment;

import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.yaml.metrics.MetricValues;
import aiai.ai.yaml.metrics.MetricsUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@Profile("launchpad")
@Slf4j
public class MetricsMaxValueCollector {

    private final TaskRepository taskRepository;

    public MetricsMaxValueCollector(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public double calcMaxValueForMetrics(long featureId) {

        try (Stream<Object[]> stream = taskRepository.findMetricsByExperimentFeatureId(featureId) ) {
            //noinspection UnnecessaryLocalVariable
            double value = stream
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
}
