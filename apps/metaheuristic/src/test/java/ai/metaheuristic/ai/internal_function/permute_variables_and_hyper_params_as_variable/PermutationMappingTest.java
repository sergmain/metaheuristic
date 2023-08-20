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

package ai.metaheuristic.ai.internal_function.permute_variables_and_hyper_params_as_variable;

import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.utils.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ai.metaheuristic.ai.dispatcher.variable.VariableUtils.permutationAsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Serge
 * Date: 3/10/2021
 * Time: 3:34 AM
 */
public class PermutationMappingTest {

    @Test
    public void testJsonMapping() throws IOException {

        Variable sv1 = new Variable(41L, 0, true, false, null, "name1", 13L, "taskContextId1",
                Timestamp.from(Instant.now()), "filename1", "");

        VariableData.Permutation p1 = new VariableData.Permutation(
                List.of(new VariableUtils.VariableHolder(sv1)),
                "permutedVariableName1",
                Map.of("inline1", Map.of("key1", "val1")),
                "inlineVariableName1",
                Map.of("key1", "val1"),
                true
        );


        Variable sv2 = new Variable(42L, 0, true, false, null, "name2", 13L, "taskContextId2", Timestamp.from(Instant.now()), "filename2", "");

        VariableData.Permutation p2 = new VariableData.Permutation(
                List.of(new VariableUtils.VariableHolder(sv2)),
                "permutedVariableName2",
                Map.of("inline2", Map.of("key2", "val2")),
                "inlineVariableName2",
                Map.of("key2", "val2"),
                true
        );


        String s = permutationAsString(p1) + "\n" + permutationAsString(p2);

        StringReader sr = new StringReader(s);
        List<String> lines = IOUtils.readLines(sr);

        assertEquals(2, lines.size());

        VariableData.Permutation r1 = VariableUtils.asStringAsPermutation(lines.get(0));
        assertEquals(p1.permutedVariables.size(), r1.permutedVariables.size());
        assertEquals(1, r1.permutedVariables.size());
        assertEquals(sv1, r1.permutedVariables.get(0).variable);

        assertEquals(p1.permutedVariableName, r1.permutedVariableName);
        assertTrue(CollectionUtils.isEquals(p1.inlines.keySet(), r1.inlines.keySet()));
        assertEquals(1, p1.inlines.keySet().size());
        assertEquals("inline1", new ArrayList<>(p1.inlines.keySet()).get(0));
        assertTrue(CollectionUtils.isMapEquals(p1.inlines.get("inline1"), r1.inlines.get("inline1")));

        assertEquals(p1.inlineVariableName, r1.inlineVariableName);

        assertTrue(CollectionUtils.isMapEquals(p1.inlinePermuted, r1.inlinePermuted));
    }
}
