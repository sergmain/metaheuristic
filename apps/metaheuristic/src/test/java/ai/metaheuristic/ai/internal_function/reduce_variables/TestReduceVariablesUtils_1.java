/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 11/13/2021
 * Time: 6:53 PM
 */
@Disabled
@Execution(ExecutionMode.CONCURRENT)
@DisplayName("experiments postponed for very long period of time")
public class TestReduceVariablesUtils_1 {

    @Test
    public void testExternal_17() throws IOException {
        UtilsForTestReduceVariables.extracted_1(List.of("variable-4487360-aggregatedResult11.zip"), UtilsForTestReduceVariables::filterStr);
    }

    @Test
    public void testExternal_18() throws IOException {
        UtilsForTestReduceVariables.extracted_1(List.of("variable-857258-aggregatedResult1.zip"), UtilsForTestReduceVariables::filterStr);
    }

}
