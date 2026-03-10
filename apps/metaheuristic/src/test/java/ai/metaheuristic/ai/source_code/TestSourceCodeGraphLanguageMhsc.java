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

package ai.metaheuristic.ai.source_code;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.source_code.graph.SourceCodeGraphFactory;
import ai.metaheuristic.ai.dispatcher.source_code.graph.SourceCodeGraphLanguageMhsc;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static ai.metaheuristic.ai.dispatcher.data.SourceCodeData.SourceCodeGraph;
import static ai.metaheuristic.ai.dispatcher.exec_context.ExecContextProcessGraphService.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the .mhsc DSL parser (SourceCodeGraphLanguageMhsc).
 * Parses .mhsc files and compares the resulting SourceCodeGraph with the YAML-parsed equivalent.
 */
@Execution(ExecutionMode.CONCURRENT)
public class TestSourceCodeGraphLanguageMhsc {

    @Test
    public void test_mhsc_short_parses_without_error() throws IOException {
        String mhscSource = IOUtils.resourceToString("/source_code/mhsc/mhdg-rg-flat-short-1.0.0.mhsc", StandardCharsets.UTF_8);
        AtomicLong contextId = new AtomicLong();
        SourceCodeGraph mhscGraph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhscSource, () -> "" + contextId.incrementAndGet());

        assertNotNull(mhscGraph);
        // Should have mh.finish auto-added
        ExecContextData.ProcessVertex finishVertex = findVertex(mhscGraph.processGraph, Consts.MH_FINISH_FUNCTION);
        assertNotNull(finishVertex, "mh.finish should be auto-added");
        assertEquals(1, findLeafs(mhscGraph).size(), "Should have exactly 1 leaf (mh.finish). Graph:\n" + asString(mhscGraph.processGraph));
    }

    @Test
    public void test_mhsc_short_vs_yaml_process_count() throws IOException {
        SourceCodeGraph yamlGraph = parseYaml("/source_code/yaml/mhdg-rg-flat-short-1.0.0.yaml");
        SourceCodeGraph mhscGraph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-short-1.0.0.mhsc");

        // Same number of processes
        assertEquals(yamlGraph.processes.size(), mhscGraph.processes.size(),
                "Process count mismatch. YAML=" + yamlGraph.processes.size() + " MHSC=" + mhscGraph.processes.size() +
                "\nYAML processes: " + yamlGraph.processes.stream().map(p -> p.processCode).toList() +
                "\nMHSC processes: " + mhscGraph.processes.stream().map(p -> p.processCode).toList());
    }

    @Test
    public void test_mhsc_short_vs_yaml_vertex_count() throws IOException {
        SourceCodeGraph yamlGraph = parseYaml("/source_code/yaml/mhdg-rg-flat-short-1.0.0.yaml");
        SourceCodeGraph mhscGraph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-short-1.0.0.mhsc");

        assertEquals(yamlGraph.processGraph.vertexSet().size(), mhscGraph.processGraph.vertexSet().size(),
                "Vertex count mismatch.\nYAML graph:\n" + asString(yamlGraph.processGraph) +
                "\nMHSC graph:\n" + asString(mhscGraph.processGraph));
    }

    @Test
    public void test_mhsc_short_vs_yaml_process_codes() throws IOException {
        SourceCodeGraph yamlGraph = parseYaml("/source_code/yaml/mhdg-rg-flat-short-1.0.0.yaml");
        SourceCodeGraph mhscGraph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-short-1.0.0.mhsc");

        // Every process code in YAML graph must exist in MHSC graph
        for (ExecContextParamsYaml.Process yamlProcess : yamlGraph.processes) {
            ExecContextParamsYaml.Process mhscProcess = mhscGraph.processes.stream()
                    .filter(p -> p.processCode.equals(yamlProcess.processCode))
                    .findFirst()
                    .orElse(null);
            assertNotNull(mhscProcess, "Process code '" + yamlProcess.processCode + "' missing in MHSC graph");
        }
    }

    @Test
    public void test_mhsc_short_vs_yaml_process_details() throws IOException {
        SourceCodeGraph yamlGraph = parseYaml("/source_code/yaml/mhdg-rg-flat-short-1.0.0.yaml");
        SourceCodeGraph mhscGraph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-short-1.0.0.mhsc");

        for (ExecContextParamsYaml.Process yamlProcess : yamlGraph.processes) {
            ExecContextParamsYaml.Process mhscProcess = mhscGraph.processes.stream()
                    .filter(p -> p.processCode.equals(yamlProcess.processCode))
                    .findFirst()
                    .orElse(null);
            if (mhscProcess == null) {
                continue; // covered by test_mhsc_short_vs_yaml_process_codes
            }

            String code = yamlProcess.processCode;

            // Function code and context
            assertEquals(yamlProcess.function.code, mhscProcess.function.code,
                    "Function code mismatch for process '" + code + "'");
            assertEquals(yamlProcess.function.context, mhscProcess.function.context,
                    "Function context mismatch for process '" + code + "'");

            // Timeout
            assertEquals(yamlProcess.timeoutBeforeTerminate, mhscProcess.timeoutBeforeTerminate,
                    "Timeout mismatch for process '" + code + "'");

            // Input count
            assertEquals(yamlProcess.inputs.size(), mhscProcess.inputs.size(),
                    "Input count mismatch for process '" + code + "'" +
                    "\nYAML inputs: " + yamlProcess.inputs.stream().map(v -> v.name).toList() +
                    "\nMHSC inputs: " + mhscProcess.inputs.stream().map(v -> v.name).toList());

            // Output count
            assertEquals(yamlProcess.outputs.size(), mhscProcess.outputs.size(),
                    "Output count mismatch for process '" + code + "'" +
                    "\nYAML outputs: " + yamlProcess.outputs.stream().map(v -> v.name).toList() +
                    "\nMHSC outputs: " + mhscProcess.outputs.stream().map(v -> v.name).toList());

            // Input names
            for (int i = 0; i < yamlProcess.inputs.size(); i++) {
                assertEquals(yamlProcess.inputs.get(i).name, mhscProcess.inputs.get(i).name,
                        "Input name mismatch at index " + i + " for process '" + code + "'");
            }

            // Output names and types
            for (int i = 0; i < yamlProcess.outputs.size(); i++) {
                ExecContextParamsYaml.Variable yv = yamlProcess.outputs.get(i);
                ExecContextParamsYaml.Variable mv = mhscProcess.outputs.get(i);
                assertEquals(yv.name, mv.name,
                        "Output name mismatch at index " + i + " for process '" + code + "'");
                assertEquals(yv.type, mv.type,
                        "Output type mismatch for variable '" + yv.name + "' in process '" + code + "'");
                assertEquals(yv.getNullable(), mv.getNullable(),
                        "Output nullable mismatch for variable '" + yv.name + "' in process '" + code + "'");
                assertEquals(yv.ext, mv.ext,
                        "Output ext mismatch for variable '" + yv.name + "' in process '" + code + "'");
            }

            // Condition
            assertEquals(yamlProcess.condition, mhscProcess.condition,
                    "Condition mismatch for process '" + code + "'");

            // Cache
            if (yamlProcess.cache != null) {
                assertNotNull(mhscProcess.cache, "Cache should not be null for process '" + code + "'");
                assertEquals(yamlProcess.cache.enabled, mhscProcess.cache.enabled,
                        "Cache enabled mismatch for process '" + code + "'");
            }

            // Logic (subprocess type)
            assertEquals(yamlProcess.logic, mhscProcess.logic,
                    "Logic mismatch for process '" + code + "'");

            // Meta count
            assertEquals(yamlProcess.metas.size(), mhscProcess.metas.size(),
                    "Meta count mismatch for process '" + code + "'" +
                    "\nYAML metas: " + yamlProcess.metas +
                    "\nMHSC metas: " + mhscProcess.metas);
        }
    }

    @Test
    public void test_mhsc_short_vs_yaml_variables() throws IOException {
        SourceCodeGraph yamlGraph = parseYaml("/source_code/yaml/mhdg-rg-flat-short-1.0.0.yaml");
        SourceCodeGraph mhscGraph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-short-1.0.0.mhsc");

        // Inputs
        assertEquals(yamlGraph.variables.inputs.size(), mhscGraph.variables.inputs.size(),
                "Variable input count mismatch");
        for (int i = 0; i < yamlGraph.variables.inputs.size(); i++) {
            assertEquals(yamlGraph.variables.inputs.get(i).name, mhscGraph.variables.inputs.get(i).name,
                    "Variable input name mismatch at index " + i);
        }

        // Outputs
        assertEquals(yamlGraph.variables.outputs.size(), mhscGraph.variables.outputs.size(),
                "Variable output count mismatch");
    }

    @Test
    public void test_unquote() {
        assertEquals("hello", SourceCodeGraphLanguageMhsc.unquote("\"hello\""));
        assertEquals("hello", SourceCodeGraphLanguageMhsc.unquote("'hello'"));
        assertEquals("he\"llo", SourceCodeGraphLanguageMhsc.unquote("\"he\\\"llo\""));
        assertEquals("plain", SourceCodeGraphLanguageMhsc.unquote("plain"));
    }

    // ============ Helpers ============

    private SourceCodeGraph parseYaml(String resourcePath) throws IOException {
        String source = IOUtils.resourceToString(resourcePath, StandardCharsets.UTF_8);
        AtomicLong contextId = new AtomicLong();
        return SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.yaml, source, () -> "" + contextId.incrementAndGet());
    }

    private SourceCodeGraph parseMhsc(String resourcePath) throws IOException {
        String source = IOUtils.resourceToString(resourcePath, StandardCharsets.UTF_8);
        AtomicLong contextId = new AtomicLong();
        return SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, source, () -> "" + contextId.incrementAndGet());
    }
}
