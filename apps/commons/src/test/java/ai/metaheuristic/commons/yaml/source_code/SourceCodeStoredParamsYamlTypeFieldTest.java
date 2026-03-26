/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.commons.yaml.source_code;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.commons.exceptions.WrongVersionOfParamsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization test for SourceCodeStoredParamsYaml#type field round-trip.
 */
@Execution(ExecutionMode.CONCURRENT)
public class SourceCodeStoredParamsYamlTypeFieldTest {

    @Test
    public void test_typeField_roundTrip() {
        // Setup: create a SourceCodeStoredParamsYaml with type set
        SourceCodeStoredParamsYaml original = new SourceCodeStoredParamsYaml();
        original.source = "test-source";
        original.lang = EnumsApi.SourceCodeLang.yaml;
        original.type = "batch";

        // Serialize to YAML string
        String yaml = SourceCodeStoredParamsYamlUtils.BASE_YAML_UTILS.toString(original);

        // CORRECT behavior: round-trip should preserve the type field
        SourceCodeStoredParamsYaml restored = SourceCodeStoredParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        assertEquals("batch", restored.type, "type field should survive round-trip");
    }
}
