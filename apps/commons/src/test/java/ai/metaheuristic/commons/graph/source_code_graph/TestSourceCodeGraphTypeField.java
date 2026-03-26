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

package ai.metaheuristic.commons.graph.source_code_graph;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.SourceCodeGraph;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the 'type' field on SourceCodeGraph, parsed from both YAML and MHSC formats.
 */
@Execution(ExecutionMode.CONCURRENT)
public class TestSourceCodeGraphTypeField {

    // === YAML tests ===

    @Test
    public void test_yaml_with_type() throws IOException {
        String yaml = IOUtils.resourceToString("/source_code/yaml/simple-with-type.yaml", StandardCharsets.UTF_8);
        SourceCodeGraph scg = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.yaml, yaml);

        assertNotNull(scg);
        assertEquals("test-source-code-with-type", scg.uid);
        assertEquals("batch-processing", scg.type);
    }

    @Test
    public void test_yaml_without_type() throws IOException {
        String yaml = IOUtils.resourceToString("/source_code/yaml/simple-without-type.yaml", StandardCharsets.UTF_8);
        SourceCodeGraph scg = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.yaml, yaml);

        assertNotNull(scg);
        assertEquals("test-source-code-without-type", scg.uid);
        assertNull(scg.type);
    }

    // === MHSC tests ===

    @Test
    public void test_mhsc_with_type() throws IOException {
        String mhsc = IOUtils.resourceToString("/source_code/mhsc/simple-with-type.mhsc", StandardCharsets.UTF_8);
        SourceCodeGraph scg = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);

        assertNotNull(scg);
        assertEquals("test-source-code-with-type", scg.uid);
        assertEquals("batch-processing", scg.type);
    }

    @Test
    public void test_mhsc_without_type() {
        String mhsc = """
                source "no-type-test" {
                    p1 := some-func {
                        <- in1
                        -> out1
                    }
                }
                """;
        SourceCodeGraph scg = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);

        assertNotNull(scg);
        assertEquals("no-type-test", scg.uid);
        assertNull(scg.type);
    }

    // === Cross-format consistency ===

    @Test
    public void test_yaml_and_mhsc_type_match() throws IOException {
        String yaml = IOUtils.resourceToString("/source_code/yaml/simple-with-type.yaml", StandardCharsets.UTF_8);
        String mhsc = IOUtils.resourceToString("/source_code/mhsc/simple-with-type.mhsc", StandardCharsets.UTF_8);

        SourceCodeGraph yamlGraph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.yaml, yaml);
        SourceCodeGraph mhscGraph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);

        assertEquals(yamlGraph.type, mhscGraph.type, "type field should match between YAML and MHSC");
        assertEquals(yamlGraph.uid, mhscGraph.uid, "uid should match between YAML and MHSC");
    }
}
