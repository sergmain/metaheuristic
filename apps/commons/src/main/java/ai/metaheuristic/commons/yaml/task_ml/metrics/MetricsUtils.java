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
package ai.metaheuristic.commons.yaml.task_ml.metrics;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.YamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;

@Slf4j
public class MetricsUtils {

    private static Yaml getYaml() {
        return YamlUtils.init(Metrics.class);
    }

    public static Yaml getValueYaml() {
        return YamlUtils.init(MetricValues.class);
    }

    public static String toString(Metrics config) {
        return YamlUtils.toString(config, getYaml());
    }

    public static Metrics to(String s) {
        return (Metrics) YamlUtils.to(s, getYaml());
    }

    public static Metrics to(InputStream is) {
        return (Metrics) YamlUtils.to(is, getYaml());
    }

    public static Metrics to(File file) {
        return (Metrics) YamlUtils.to(file, getYaml());
    }


    public static MetricValues getValues(Metrics metrics) {
        if (metrics==null || metrics.getStatus()!= EnumsApi.MetricsStatus.Ok) {
            return new MetricValues();
        }
        MetricValues metricValues = getMetricValues(metrics.metrics);
        return metricValues;
    }

    public static MetricValues getMetricValues(String metrics) {
        return (MetricValues) YamlUtils.to(metrics, getValueYaml());
    }
}
