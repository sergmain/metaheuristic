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

package ai.metaheuristic.ai.internal_function.reduce_values;

import ai.metaheuristic.ai.dispatcher.data.ReduceValuesData;
import ai.metaheuristic.ai.dispatcher.internal_functions.reduce_values.ReduceValuesUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 11/13/2021
 * Time: 6:53 PM
 */
public class TestReduceValuesUtils {

    @Test
    public void test() {
        final URL url = TestReduceValuesUtils.class.getResource("/bin/variable-75492-aggregatedResult.zip");
        assertNotNull(url);
        File zip = new File(url.getFile());
        assertTrue(zip.exists());

        ReduceValuesData.VariablesData data = ReduceValuesUtils.loadData(zip);

        assertFalse(data.permutedVariables.isEmpty());
    }
}
