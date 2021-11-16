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

package ai.metaheuristic.ai.internal_function.reduce_variables;

import ai.metaheuristic.ai.dispatcher.data.ReduceVariablesData;
import ai.metaheuristic.ai.dispatcher.internal_functions.reduce_values.ReduceVariablesEnums;
import ai.metaheuristic.ai.dispatcher.internal_functions.reduce_values.ReduceVariablesUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 11/13/2021
 * Time: 6:53 PM
 */
public class TestReduceVariablesUtils {

    @Test
    public void test() {
        final URL url = TestReduceVariablesUtils.class.getResource("/bin/variable-75492-aggregatedResult.zip");
        assertNotNull(url);
        File zip = new File(url.getFile());
        assertTrue(zip.exists());

        ReduceVariablesData.Config config = new ReduceVariablesData.Config();
        config.policy = ReduceVariablesEnums.Policy.reduce_instance;
        config.fixName = true;
        config.fittingVar = "fitting";
        config.metricsVar = "metrics";
        config.metricsName = "sum";
        config.reduceByValue = List.of("activation", "batchSize", "epoch", "optimizer", "RNN", "seed", "timeSteps");
        config.reduceByInstance = List.of("binaryClusters1", "binaryDrawWithFrequency", "clusterCount1", "clusterSize1", "distribOfFreqFull", "matrixOfWinning");

        ReduceVariablesData.VariablesData data = ReduceVariablesUtils.loadData(zip, config);

        assertFalse(data.permutedVariables.isEmpty());
    }

    @Disabled
    @Test
    public void testExternal() {
        File zip = new File("variable-2653425-aggregatedResult1.zip");
        assertTrue(zip.exists());

        ReduceVariablesData.Config config = new ReduceVariablesData.Config();
        config.policy = ReduceVariablesEnums.Policy.reduce_instance;
        config.fixName = true;
        config.fittingVar = "fitting";
        config.metricsVar = "metrics";
        config.metricsName = "sum";
        config.reduceByValue = List.of("activation", "batchSize", "epoch", "optimizer", "RNN", "seed", "timeSteps");
        config.reduceByInstance = List.of("binaryClusters1", "binaryDrawWithFrequency", "clusterCount1", "clusterSize1", "distribOfFreqFull", "matrixOfWinning");


        ReduceVariablesData.ReduceVariablesResult result = ReduceVariablesUtils.reduceVariables(zip, config);

        assertFalse(result.byValue.isEmpty());
        assertFalse(result.byInstance.isEmpty());
    }
}
