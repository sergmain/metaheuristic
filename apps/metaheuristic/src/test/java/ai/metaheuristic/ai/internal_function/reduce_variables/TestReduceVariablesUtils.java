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

package ai.metaheuristic.ai.internal_function.reduce_variables;

import ai.metaheuristic.ai.dispatcher.data.ReduceVariablesData;
import ai.metaheuristic.ai.dispatcher.internal_functions.reduce_values.ReduceVariablesUtils;
import ai.metaheuristic.ai.yaml.reduce_values_function.ReduceVariablesConfigParamsYaml;
import ai.metaheuristic.ai.yaml.reduce_values_function.ReduceVariablesConfigParamsYamlUtils;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 11/13/2021
 * Time: 6:53 PM
 */
@Disabled
public class TestReduceVariablesUtils {

    @Test
    public void test() throws IOException {
        final URL url = TestReduceVariablesUtils.class.getResource("/bin/variable-75492-aggregatedResult.zip");
        assertNotNull(url);
        Path zip = new File(url.getFile()).toPath();
        assertTrue(Files.exists(zip));

        String yaml = """
                version: 1
                config:
                    fixName: true
                    fittingVar: fitting
                    metricsVar: metrics
                    metricsName: sum
                    reduceByValue:
                        activation: activation1
                        batchSize: batchSize1
                        epoch: epoch1
                        optimizer: optimizer1
                        RNN: RNN1
                        seed: seed1
                        timeSteps: timeSteps1
                    reduceByInstance:
                        - input: binaryClusters1
                          inputIs: isBinaryClusters1
                          outputIs: isBinaryClusters1New1
                        - input: binaryDrawWithFrequency
                          inputIs: isBinaryDrawWithFrequency
                          outputIs: isBinaryDrawWithFrequencyNew1
                        - input: clusterCount1
                          inputIs: isClusterCount1
                          outputIs: isClusterCount1News1
                        - input: clusterSize1
                          inputIs: isClusterSize1
                          outputIs: isClusterSize1New1
                        - input: distribOfFreqFull
                          inputIs: isDistribOfFreqFull
                          outputIs: isDistribOfFreqFullNew1
                        - input: matrixOfWinning
                          inputIs: isMatrixOfWinning
                          outputIs: isMatrixOfWinningNew1
                    reduces:
                        - policy: reduce_value
                          reducePercent: 50
                          variable: activation
                """;

        ReduceVariablesConfigParamsYaml config = ReduceVariablesConfigParamsYamlUtils.BASE_YAML_UTILS.to(yaml);

        ReduceVariablesData.VariablesData data = new ReduceVariablesData.VariablesData();

        FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
        Path temp = fs.getPath("/temp");
        Path actualTemp = Files.createDirectory(temp);


        ReduceVariablesUtils.loadData(actualTemp, data, zip, config, (o)->{});

        assertFalse(data.permutedVariables.isEmpty());
    }

}
