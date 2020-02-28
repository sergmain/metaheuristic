/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dispatcher.experiment;

import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class MetricsMaxValueCollector {

    private final TaskRepository taskRepository;

    public double calcMaxValueForMetrics(ExperimentParamsYaml epy, Long featureId) {
        List<Long> ids = epy.getTaskFeatureIds(featureId);
        if (ids.isEmpty()) {
            return 0.0;
        }

        double max = 0.0;
        for (Long taskId : ids) {
            max = Math.max(max, getMetrics(taskId));
        }
        return max;

/*
        List<Object[]> list = taskRepository.findMetricsByIds(ids);
        //noinspection UnnecessaryLocalVariable
        double value = list.stream()
                .map(o -> {
//    metrics: |
//      metrics: "values:\r\n  sum: 33\r\n  sum_6: 63\r\n  sum_7: 75\r\n  sum_8: 90\r\n  sum_9:\
//        \ 101\r\n"
//      status: Ok
//    fitted: 'UNDERFITTED'
//    version: 1


                    if (S.b((String)o[1])) {
                        return null;
                    }
                    TaskMachineLearningYaml tmly = TaskMachineLearningYamlUtils.BASE_YAML_UTILS.to((String)o[1]);
                    MetricValues metricValues = MetricsUtils.getValues( tmly.metrics );
                    if (metricValues==null) {
                        return null;
                    }
                    String metricKey=null;
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
*/
    }

    private double getMetrics(Long taskId) {
        if (true) {
            throw new NotImplementedException("not yet");
        }
        return 0;
    }
}
