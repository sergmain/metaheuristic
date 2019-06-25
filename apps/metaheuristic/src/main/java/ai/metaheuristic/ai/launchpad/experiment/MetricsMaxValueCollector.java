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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
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

//        @Query(value="select t.id, t.metrics from TaskImpl t, ExperimentTaskFeature f " +
//                "where t.id=f.taskId and f.featureId=:experimentFeatureId ")
//        Stream<Object[]> findMetricsByExperimentFeatureId(long experimentFeatureId);

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
