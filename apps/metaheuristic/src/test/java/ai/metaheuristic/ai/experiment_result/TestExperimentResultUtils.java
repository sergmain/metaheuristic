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

package ai.metaheuristic.ai.experiment_result;

import ai.metaheuristic.api.data.experiment_result.ExperimentResultParams;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ai.metaheuristic.ai.dispatcher.experiment_result.ExperimentResultTopLevelService.getMetricsNames;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Serge
 * Date: 4/28/2020
 * Time: 5:56 PM
 */
public class TestExperimentResultUtils {

    @Test
    public void test() {
        ExperimentResultParams.ExperimentFeature feature = new ExperimentResultParams.ExperimentFeature();
        feature.maxValues.put("a1", 1.0);
        feature.maxValues.put("a2", 2.0);
        feature.maxValues.put("a3", 3.0);
        List<String> names = getMetricsNames(feature);

        assertNotNull(names);
        assertEquals(3, names.size());

        assertEquals("a1", names.get(0));
        assertEquals("a2", names.get(1));
        assertEquals("a3", names.get(2));
    }
}
