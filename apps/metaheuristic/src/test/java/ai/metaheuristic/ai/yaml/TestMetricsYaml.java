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
package ai.metaheuristic.ai.yaml;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.task_ml.metrics.MetricValues;
import ai.metaheuristic.commons.yaml.task_ml.metrics.Metrics;
import ai.metaheuristic.commons.yaml.task_ml.metrics.MetricsUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class TestMetricsYaml {

    @Test
    public void testUnmarshalling() {
        String yaml = "comment: 'The last draw. num_of_intersected[0]: 0, n_total_intersected.sum(): 380,\n" +
                "  adjustTo: 0.14285714285714285, intersected_sum_as_int: 54'\n" +
                "values:\n" +
                "  sum: 54\n" +
                "  sum_6: 112\n" +
                "  sum_7: 122\n" +
                "  sum_8: 147\n" +
                "  sum_9: 160\n" +
                "  sum_norm: 380\n" +
                "  sum_norm_6: 79\n" +
                "  sum_norm_7: 86\n" +
                "  sum_norm_8: 103\n" +
                "  sum_norm_9: 112\n";

        MetricValues values = MetricsUtils.getMetricValues(yaml);

    }

    @Test
    public void testValueYaml() {
        String s = "values:\n  sum: 334\r\n  sum_0: 71\r\n  sum_1: 79\r\n  sum_2: 89\r\n  sum_3: 95\r\n";
        internalTestValueYaml(s);
    }

    private static void internalTestValueYaml(String s) {
        Metrics metrics = new Metrics();
        metrics.setMetrics(s);
        metrics.setStatus(EnumsApi.MetricsStatus.Ok);

        MetricValues values = MetricsUtils.getValues(metrics);

        assertNotNull(values);
        assertTrue(values.values.containsKey("sum"));
        assertTrue(values.values.containsKey("sum_0"));
        assertTrue(values.values.containsKey("sum_1"));
        assertTrue(values.values.containsKey("sum_2"));
        assertTrue(values.values.containsKey("sum_3"));
        assertEquals(BigDecimal.valueOf(334), values.values.get("sum"));
        assertEquals(BigDecimal.valueOf(71), values.values.get("sum_0"));
        assertEquals(BigDecimal.valueOf(79), values.values.get("sum_1"));
        assertEquals(BigDecimal.valueOf(89), values.values.get("sum_2"));
        assertEquals(BigDecimal.valueOf(95), values.values.get("sum_3"));
    }
}
