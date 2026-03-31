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

import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.exceptions.SourceCodeGraphException;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import ai.metaheuristic.api.data.SourceCodeGraph;
import static ai.metaheuristic.commons.graph.ExecContextProcessGraphService.*;
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
        SourceCodeGraph mhscGraph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhscSource);

        assertNotNull(mhscGraph);
        // Should have mh.finish auto-added
        ExecContextApiData.ProcessVertex finishVertex = findVertex(mhscGraph.processGraph, CommonConsts.MH_FINISH_FUNCTION);
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

    @Test
    public void test_priority_negative() throws IOException {
        String mhsc = """
            source "test" {
                my-proc := some-func {
                    priority -1
                    timeout 10
                }
            }""";
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("my-proc"))
                .findFirst().orElseThrow();
        assertEquals(-1, p.priority);
    }

    @Test
    public void test_priority_positive() throws IOException {
        String mhsc = """
                source "test" {
                    my-proc := some-func {
                        priority 5
                        timeout 10
                    }
                }""";
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("my-proc"))
                .findFirst().orElseThrow();
        assertEquals(5, p.priority);
    }

    @Test
    public void test_priority_zero() throws IOException {
        String mhsc = """
                source "test" {
                    my-proc := some-func {
                        priority 0
                    }
                }""";
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("my-proc"))
                .findFirst().orElseThrow();
        assertEquals(0, p.priority);
    }

    // ===================== Factorial: parse without errors =====================

    @Test
    public void test_factorial_main_parses() throws IOException {
        String mhsc = IOUtils.resourceToString("/source_code/mhsc/mh-factorial-main-1.8.mhsc", StandardCharsets.UTF_8);
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        assertNotNull(graph);
        assertEquals(1, findLeafs(graph).size(), "Graph:\n" + asString(graph.processGraph));
    }

    @Test
    public void test_factorial_recursion_parses() throws IOException {
        String mhsc = IOUtils.resourceToString("/source_code/mhsc/mh-factorial-recursion-1.17.mhsc", StandardCharsets.UTF_8);
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        assertNotNull(graph);
        assertEquals(1, findLeafs(graph).size(), "Graph:\n" + asString(graph.processGraph));
    }

    // ===================== Factorial main: structural tests =====================

    @Test
    public void test_factorial_main_globals() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mh-factorial-main-1.8.mhsc");
        assertNotNull(graph.variables.globals);
        assertEquals(3, graph.variables.globals.size());
        assertTrue(graph.variables.globals.contains("mh_int_0"));
        assertTrue(graph.variables.globals.contains("mh_int_1"));
        assertTrue(graph.variables.globals.contains("mh_int_5"));
    }

    @Test
    public void test_factorial_main_process_count() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mh-factorial-main-1.8.mhsc");
        // 1 declared process + 1 auto-added mh.finish
        assertEquals(2, graph.processes.size(),
                "Processes: " + graph.processes.stream().map(p -> p.processCode).toList());
    }

    @Test
    public void test_factorial_main_exec_source_code_process() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mh-factorial-main-1.8.mhsc");
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("mh.exec-source-code"))
                .findFirst().orElseThrow();
        assertEquals("mh.exec-source-code", p.function.code);
        assertEquals(EnumsApi.FunctionExecContext.internal, p.function.context);
        assertEquals(3, p.inputs.size());
        assertEquals(1, p.outputs.size());
        assertEquals("resultOutput", p.outputs.get(0).name);
        assertEquals(".txt", p.outputs.get(0).ext);
        assertEquals(1, p.metas.size());
    }

    // ===================== Factorial recursion: structural tests =====================

    @Test
    public void test_factorial_recursion_variables() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mh-factorial-recursion-1.17.mhsc");
        assertEquals(3, graph.variables.inputs.size());
        assertEquals("currIndex", graph.variables.inputs.get(0).name);
        assertEquals("inputValue", graph.variables.inputs.get(1).name);
        assertEquals("factorialOf", graph.variables.inputs.get(2).name);
        assertEquals(1, graph.variables.outputs.size());
        assertEquals("factorialResult", graph.variables.outputs.get(0).name);
    }

    @Test
    public void test_factorial_recursion_process_count() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mh-factorial-recursion-1.17.mhsc");
        // mh.evaluation0, mh.nop (with 4 sequential children), mh.finish = 7
        assertEquals(7, graph.processes.size(),
                "Processes: " + graph.processes.stream().map(p -> p.processCode).toList());
    }

    @Test
    public void test_factorial_recursion_vertex_count() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mh-factorial-recursion-1.17.mhsc");
        assertEquals(7, graph.processGraph.vertexSet().size(),
                "Graph:\n" + asString(graph.processGraph));
    }

    @Test
    public void test_factorial_recursion_evaluation0_condition() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mh-factorial-recursion-1.17.mhsc");
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("mh.evaluation0"))
                .findFirst().orElseThrow();
        assertEquals("mh.evaluation", p.function.code);
        assertEquals(EnumsApi.FunctionExecContext.internal, p.function.context);
        // condition: currIndex > factorialOf - 1
        assertNotNull(p.condition);
        assertEquals("currIndex > factorialOf - 1", p.condition);
    }

    @Test
    public void test_factorial_recursion_nop_condition() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mh-factorial-recursion-1.17.mhsc");
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("mh.nop"))
                .findFirst().orElseThrow();
        assertNotNull(p.condition);
        assertEquals("currIndex < factorialOf", p.condition);
        assertEquals(EnumsApi.SourceCodeSubProcessLogic.sequential, p.logic);
    }

    @Test
    public void test_factorial_recursion_multiply_process() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mh-factorial-recursion-1.17.mhsc");
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("mh.multiply_1.2"))
                .findFirst().orElseThrow();
        assertEquals("mh.multiply_1.2", p.function.code);
        assertEquals(EnumsApi.FunctionExecContext.external, p.function.context);
        assertEquals(2, p.inputs.size());
        assertEquals(1, p.outputs.size());
    }

    @Test
    public void test_factorial_recursion_sequential_chain() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mh-factorial-recursion-1.17.mhsc");
        // mh.evaluation1 -> mh.multiply_1.2 -> mh.exec-source-code -> mh.evaluation2
        ExecContextApiData.ProcessVertex eval1 = findVertex(graph.processGraph, "mh.evaluation1");
        assertNotNull(eval1);
        List<ExecContextApiData.ProcessVertex> afterEval1 = findTargets(graph.processGraph, eval1.process);
        assertEquals(1, afterEval1.size());
        assertEquals("mh.multiply_1.2", afterEval1.get(0).process);

        List<ExecContextApiData.ProcessVertex> afterMultiply = findTargets(graph.processGraph, "mh.multiply_1.2");
        assertEquals(1, afterMultiply.size());
        assertEquals("mh.exec-source-code", afterMultiply.get(0).process);

        List<ExecContextApiData.ProcessVertex> afterExec = findTargets(graph.processGraph, "mh.exec-source-code");
        assertEquals(1, afterExec.size());
        assertEquals("mh.evaluation2", afterExec.get(0).process);
    }

    // ===================== em-stat: parse + structural =====================

    @Test
    public void test_em_stat_parses() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/em-stat-5.0.42-ric-411.mhsc");
        assertNotNull(graph);
        assertEquals(1, findLeafs(graph).size(), "Graph:\n" + asString(graph.processGraph));
    }

    @Test
    public void test_em_stat_process_count() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/em-stat-5.0.42-ric-411.mhsc");
        // 7 top-level + 1 batch subprocess + 1 mh.finish = 9
        assertEquals(9, graph.processes.size(),
                "Processes: " + graph.processes.stream().map(p -> p.processCode).toList());
    }

    @Test
    public void test_em_stat_inline_variables() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/em-stat-5.0.42-ric-411.mhsc");
        assertTrue(graph.variables.inline.containsKey("list-files"));
        assertEquals("'test-files'", graph.variables.inline.get("list-files").get("dir-code"));
        assertEquals("'edition/statistics-unpacked'", graph.variables.inline.get("list-files").get("statistics-path"));
    }

    @Test
    public void test_em_stat_tag_and_priority() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/em-stat-5.0.42-ric-411.mhsc");
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("get-list-of-edition-pairs"))
                .findFirst().orElseThrow();
        assertEquals("pc13", p.tag);
        assertEquals(-1, p.priority);
        assertNotNull(p.cache);
        assertTrue(p.cache.enabled);
    }

    @Test
    public void test_em_stat_batch_subprocess_with_params() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/em-stat-5.0.42-ric-411.mhsc");
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("edition-maker-statistics"))
                .findFirst().orElseThrow();
        assertEquals("edition-maker-5.0.42", p.function.code);
        assertEquals("--collect-statistics", p.function.params);
        assertEquals(36000L, p.timeoutBeforeTerminate);
        assertEquals(0, p.triesAfterError);
    }

    // ===================== edition-maker: parse + structural =====================

    @Test
    public void test_edition_maker_parses() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/source-code-edition-maker-5.0.26-20min.mhsc");
        assertNotNull(graph);
        assertEquals(1, findLeafs(graph).size(), "Graph:\n" + asString(graph.processGraph));
    }

    @Test
    public void test_edition_maker_process_count() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/source-code-edition-maker-5.0.26-20min.mhsc");
        assertEquals(4, graph.processes.size(),
                "Processes: " + graph.processes.stream().map(p -> p.processCode).toList());
    }

    @Test
    public void test_edition_maker_array_input() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/source-code-edition-maker-5.0.26-20min.mhsc");
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("edition-maker"))
                .findFirst().orElseThrow();
        assertEquals("--fill-comment", p.function.params);
        assertEquals(1, p.inputs.size());
        assertEquals(EnumsApi.VariableContext.array, p.inputs.get(0).context);
        assertEquals(6, p.outputs.size());
    }

    @Test
    public void test_edition_maker_batch_result_processor() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/source-code-edition-maker-5.0.26-20min.mhsc");
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("mh.batch-result-processor"))
                .findFirst().orElseThrow();
        assertEquals("batch result processor", p.processName);
        assertEquals(2, p.outputs.size());
        assertEquals(3, p.metas.size());
        assertEquals(10000, p.priority);
    }

    // ===================== trafaret-image: parse + structural =====================

    @Test
    public void test_tr_image_parses() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/source-code-tr-image-1.0.10-timeout-3-min-411.mhsc");
        assertNotNull(graph);
        assertEquals(1, findLeafs(graph).size(), "Graph:\n" + asString(graph.processGraph));
    }

    @Test
    public void test_tr_image_process_count() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/source-code-tr-image-1.0.10-timeout-3-min-411.mhsc");
        assertEquals(5, graph.processes.size(),
                "Processes: " + graph.processes.stream().map(p -> p.processCode).toList());
    }

    @Test
    public void test_tr_image_two_sequential_subprocesses() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/source-code-tr-image-1.0.10-timeout-3-min-411.mhsc");
        ExecContextApiData.ProcessVertex trafaret = findVertex(graph.processGraph, "trafaret");
        assertNotNull(trafaret);
        List<ExecContextApiData.ProcessVertex> afterTrafaret = findTargets(graph.processGraph, "trafaret");
        assertEquals(1, afterTrafaret.size());
        assertEquals("image-converter", afterTrafaret.get(0).process);
    }

    @Test
    public void test_tr_image_function_params() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/source-code-tr-image-1.0.10-timeout-3-min-411.mhsc");
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("image-converter"))
                .findFirst().orElseThrow();
        assertEquals("image-converter-4.2.8", p.function.code);
        assertEquals("--dpi 150 --max-width 140 --max-height 250 --convert-wmf false --gray", p.function.params);
        assertEquals("Convert images in document", p.processName);
    }

    @Test
    public void test_tr_image_input_modifiers() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/source-code-tr-image-1.0.10-timeout-3-min-411.mhsc");
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("image-converter"))
                .findFirst().orElseThrow();
        assertEquals(2, p.inputs.size());
        assertEquals("var-after-trafaret", p.inputs.get(0).name);
        assertTrue(p.inputs.get(0).getNullable());
        assertEquals(".xml", p.inputs.get(0).ext);
    }

    // ===================== Templated mhdg-rg-flat: parse + structural =====================

    @Test
    public void test_rg_flat_templated_parses() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-1.0.0.mhsc");
        assertNotNull(graph);
        assertEquals(1, findLeafs(graph).size(), "Graph:\n" + asString(graph.processGraph));
    }

    @Test
    public void test_rg_flat_templated_vs_yaml_process_count() throws IOException {
        SourceCodeGraph yamlGraph = parseYaml("/source_code/yaml/mhdg-rg-flat-1.0.0.yaml");
        SourceCodeGraph mhscGraph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-1.0.0.mhsc");

        assertEquals(yamlGraph.processes.size(), mhscGraph.processes.size(),
                "Process count mismatch.\nYAML processes: " +
                yamlGraph.processes.stream().map(p -> p.processCode).sorted().toList() +
                "\nMHSC processes: " +
                mhscGraph.processes.stream().map(p -> p.processCode).sorted().toList());
    }

    @Test
    public void test_rg_flat_templated_vs_yaml_vertex_count() throws IOException {
        SourceCodeGraph yamlGraph = parseYaml("/source_code/yaml/mhdg-rg-flat-1.0.0.yaml");
        SourceCodeGraph mhscGraph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-1.0.0.mhsc");

        assertEquals(yamlGraph.processGraph.vertexSet().size(), mhscGraph.processGraph.vertexSet().size(),
                "Vertex count mismatch.\nYAML graph:\n" + asString(yamlGraph.processGraph) +
                "\nMHSC graph:\n" + asString(mhscGraph.processGraph));
    }

    @Test
    public void test_rg_flat_templated_vs_yaml_all_process_codes_present() throws IOException {
        SourceCodeGraph yamlGraph = parseYaml("/source_code/yaml/mhdg-rg-flat-1.0.0.yaml");
        SourceCodeGraph mhscGraph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-1.0.0.mhsc");

        for (ExecContextParamsYaml.Process yp : yamlGraph.processes) {
            ExecContextParamsYaml.Process mp = mhscGraph.processes.stream()
                    .filter(p -> p.processCode.equals(yp.processCode))
                    .findFirst().orElse(null);
            assertNotNull(mp, "Process code '" + yp.processCode + "' missing in MHSC graph");
        }
    }

    @Test
    public void test_rg_flat_templated_vs_yaml_function_codes() throws IOException {
        SourceCodeGraph yamlGraph = parseYaml("/source_code/yaml/mhdg-rg-flat-1.0.0.yaml");
        SourceCodeGraph mhscGraph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-1.0.0.mhsc");

        for (ExecContextParamsYaml.Process yp : yamlGraph.processes) {
            ExecContextParamsYaml.Process mp = mhscGraph.processes.stream()
                    .filter(p -> p.processCode.equals(yp.processCode))
                    .findFirst().orElse(null);
            if (mp == null) continue;
            assertEquals(yp.function.code, mp.function.code,
                    "Function code mismatch for process '" + yp.processCode + "'");
            assertEquals(yp.function.context, mp.function.context,
                    "Function context mismatch for process '" + yp.processCode + "'");
        }
    }

    @Test
    public void test_rg_flat_templated_vs_yaml_io_counts() throws IOException {
        SourceCodeGraph yamlGraph = parseYaml("/source_code/yaml/mhdg-rg-flat-1.0.0.yaml");
        SourceCodeGraph mhscGraph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-1.0.0.mhsc");

        for (ExecContextParamsYaml.Process yp : yamlGraph.processes) {
            ExecContextParamsYaml.Process mp = mhscGraph.processes.stream()
                    .filter(p -> p.processCode.equals(yp.processCode))
                    .findFirst().orElse(null);
            if (mp == null) continue;
            assertEquals(yp.inputs.size(), mp.inputs.size(),
                    "Input count mismatch for '" + yp.processCode + "'" +
                    "\nYAML: " + yp.inputs.stream().map(v -> v.name).toList() +
                    "\nMHSC: " + mp.inputs.stream().map(v -> v.name).toList());
            assertEquals(yp.outputs.size(), mp.outputs.size(),
                    "Output count mismatch for '" + yp.processCode + "'" +
                    "\nYAML: " + yp.outputs.stream().map(v -> v.name).toList() +
                    "\nMHSC: " + mp.outputs.stream().map(v -> v.name).toList());
        }
    }

    @Test
    public void test_rg_flat_templated_vs_yaml_timeouts() throws IOException {
        SourceCodeGraph yamlGraph = parseYaml("/source_code/yaml/mhdg-rg-flat-1.0.0.yaml");
        SourceCodeGraph mhscGraph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-1.0.0.mhsc");

        for (ExecContextParamsYaml.Process yp : yamlGraph.processes) {
            ExecContextParamsYaml.Process mp = mhscGraph.processes.stream()
                    .filter(p -> p.processCode.equals(yp.processCode))
                    .findFirst().orElse(null);
            if (mp == null) continue;
            assertEquals(yp.timeoutBeforeTerminate, mp.timeoutBeforeTerminate,
                    "Timeout mismatch for '" + yp.processCode + "'");
        }
    }

    @Test
    public void test_rg_flat_templated_vs_yaml_conditions() throws IOException {
        SourceCodeGraph yamlGraph = parseYaml("/source_code/yaml/mhdg-rg-flat-1.0.0.yaml");
        SourceCodeGraph mhscGraph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-1.0.0.mhsc");

        for (ExecContextParamsYaml.Process yp : yamlGraph.processes) {
            ExecContextParamsYaml.Process mp = mhscGraph.processes.stream()
                    .filter(p -> p.processCode.equals(yp.processCode))
                    .findFirst().orElse(null);
            if (mp == null) continue;
            assertEquals(yp.condition, mp.condition,
                    "Condition mismatch for '" + yp.processCode + "'");
        }
    }

    @Test
    public void test_rg_flat_templated_vs_yaml_cache() throws IOException {
        SourceCodeGraph yamlGraph = parseYaml("/source_code/yaml/mhdg-rg-flat-1.0.0.yaml");
        SourceCodeGraph mhscGraph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-1.0.0.mhsc");

        for (ExecContextParamsYaml.Process yp : yamlGraph.processes) {
            ExecContextParamsYaml.Process mp = mhscGraph.processes.stream()
                    .filter(p -> p.processCode.equals(yp.processCode))
                    .findFirst().orElse(null);
            if (mp == null) continue;
            if (yp.cache != null) {
                assertNotNull(mp.cache, "Cache should not be null for '" + yp.processCode + "'");
                assertEquals(yp.cache.enabled, mp.cache.enabled,
                        "Cache enabled mismatch for '" + yp.processCode + "'");
            }
        }
    }

    @Test
    public void test_rg_flat_templated_vs_yaml_logic() throws IOException {
        SourceCodeGraph yamlGraph = parseYaml("/source_code/yaml/mhdg-rg-flat-1.0.0.yaml");
        SourceCodeGraph mhscGraph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-1.0.0.mhsc");

        for (ExecContextParamsYaml.Process yp : yamlGraph.processes) {
            ExecContextParamsYaml.Process mp = mhscGraph.processes.stream()
                    .filter(p -> p.processCode.equals(yp.processCode))
                    .findFirst().orElse(null);
            if (mp == null) continue;
            assertEquals(yp.logic, mp.logic,
                    "Logic mismatch for '" + yp.processCode + "'");
        }
    }

    @Test
    public void test_rg_flat_templated_template_expansion_level0_no_parentId() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-1.0.0.mhsc");
        // Level 0 store-req should NOT have parentId input
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("mhdg-rg.store-req-0"))
                .findFirst().orElseThrow();
        assertEquals(2, p.inputs.size(),
                "Level 0 store-req should have 2 inputs (projectCode, reqJson0). Got: " +
                p.inputs.stream().map(v -> v.name).toList());
    }

    @Test
    public void test_rg_flat_templated_template_expansion_level1_has_parentId() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-1.0.0.mhsc");
        // Level 1+ store-req should have parentId input
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("mhdg-rg.store-req-1"))
                .findFirst().orElseThrow();
        assertEquals(3, p.inputs.size(),
                "Level 1 store-req should have 3 inputs (projectCode, reqJson1, parentId1). Got: " +
                p.inputs.stream().map(v -> v.name).toList());
    }

    @Test
    public void test_rg_flat_templated_parameterized_ids_resolved() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-1.0.0.mhsc");
        // Check that {L} and {L+1} are properly resolved for level 3
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("mhdg-rg.check-objectives-4"))
                .findFirst().orElseThrow();
        // Input should be requirementId3 (L=3)
        assertEquals("requirementId3", p.inputs.get(0).name);
        // Outputs should have hasObjectives4, requirementIdObj4 etc. (L+1=4)
        assertTrue(p.outputs.stream().anyMatch(v -> v.name.equals("hasObjectives4")));
        assertTrue(p.outputs.stream().anyMatch(v -> v.name.equals("requirementIdObj4")));
    }

    @Test
    public void test_rg_flat_templated_leaf_level_store_only() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-1.0.0.mhsc");
        // Level 5 batch-splitter should contain only store-req-5 and set-reset-task-id-5
        assertTrue(graph.processes.stream().anyMatch(p -> p.processCode.equals("mhdg-rg.store-req-5")));
        assertTrue(graph.processes.stream().anyMatch(p -> p.processCode.equals("mhdg-rg.set-reset-task-id-5")));
    }

    // ============ uid field tests ============

    @Test
    public void test_mhsc_short_uid_populated() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-short-1.0.0.mhsc");
        assertEquals("mhdg-rg-1.0.4", graph.uid, "uid should be extracted from source declaration");
    }

    @Test
    public void test_yaml_short_uid_populated() throws IOException {
        SourceCodeGraph graph = parseYaml("/source_code/yaml/mhdg-rg-flat-short-1.0.0.yaml");
        assertEquals("mhdg-rg-1.0.4", graph.uid, "uid should be extracted from YAML source.uid");
    }

    @Test
    public void test_factorial_main_uid() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mh-factorial-main-1.8.mhsc");
        assertEquals("mh-factorial-main-1.8", graph.uid);
    }

    @Test
    public void test_factorial_recursion_uid() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mh-factorial-recursion-1.17.mhsc");
        assertEquals("mh-factorial-recursion-1.17", graph.uid);
    }

    @Test
    public void test_mhsc_vs_yaml_uid_match() throws IOException {
        SourceCodeGraph yamlGraph = parseYaml("/source_code/yaml/mhdg-rg-flat-short-1.0.0.yaml");
        SourceCodeGraph mhscGraph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-short-1.0.0.mhsc");
        assertEquals(yamlGraph.uid, mhscGraph.uid, "YAML and MHSC should produce the same uid");
    }

    @Test
    public void test_mhsc_blank_uid_throws() {
        String mhsc = "source \"\" (strict) { mh.nop := internal mh.nop {} }";
        assertThrows(SourceCodeGraphException.class,
                () -> SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc),
                "Blank uid should throw SourceCodeGraphException");
    }

    // ============ instances field tests ============

    @Test
    public void test_mhsc_instances_from_source_option() {
        String mhsc = "source \"test-uid\" (strict, instances = 3) { mh.nop := internal mh.nop {} }";
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        assertEquals(3, graph.instances);
    }

    @Test
    public void test_mhsc_instances_default_zero() {
        String mhsc = "source \"test-uid\" (strict) { mh.nop := internal mh.nop {} }";
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        assertEquals(0, graph.instances);
    }

    @Test
    public void test_mhsc_em_stat_instances() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/em-stat-5.0.42-ric-411.mhsc");
        assertEquals(1, graph.instances, "em-stat should have instances=1");
    }

    @Test
    public void test_yaml_em_stat_instances() throws IOException {
        SourceCodeGraph yamlGraph = parseYaml("/source_code/yaml/em-stat-5.0.42-ric-411.yaml");
        assertEquals(1, yamlGraph.instances);
    }

    @Test
    public void test_mhsc_vs_yaml_instances_match() throws IOException {
        SourceCodeGraph yamlGraph = parseYaml("/source_code/yaml/em-stat-5.0.42-ric-411.yaml");
        SourceCodeGraph mhscGraph = parseMhsc("/source_code/mhsc/em-stat-5.0.42-ric-411.mhsc");
        assertEquals(yamlGraph.instances, mhscGraph.instances);
    }

    // ============ ac (access control) field tests ============

    @Test
    public void test_mhsc_ac_from_source_option() {
        String mhsc = "source \"test-uid\" (strict, ac = \"ric411,test\") { mh.nop := internal mh.nop {} }";
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        assertNotNull(graph.ac);
        assertEquals("ric411,test", graph.ac.groups);
    }

    @Test
    public void test_mhsc_ac_default_null() {
        String mhsc = "source \"test-uid\" (strict) { mh.nop := internal mh.nop {} }";
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        assertNull(graph.ac);
    }

    @Test
    public void test_yaml_ac_populated() throws IOException {
        SourceCodeGraph yamlGraph = parseYaml("/source_code/yaml/edition-maker-5.0.26.yaml");
        assertNotNull(yamlGraph.ac);
        assertFalse(yamlGraph.ac.groups.isEmpty());
    }

    @Test
    public void test_yaml_ac_null_when_absent() throws IOException {
        SourceCodeGraph yamlGraph = parseYaml("/source_code/yaml/mhdg-rg-flat-short-1.0.0.yaml");
        assertNull(yamlGraph.ac);
    }

    // ============ source-level metas tests ============

    @Test
    public void test_mhsc_source_metas_populated() {
        String mhsc = """
            source "test-uid" (strict) {
                metas {
                    mh.result-file-extension = "-result.zip"
                }
                mh.nop := internal mh.nop {}
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        assertEquals(1, graph.metas.size());
        assertEquals("-result.zip", graph.metas.get(0).get("mh.result-file-extension"));
    }

    @Test
    public void test_mhsc_source_metas_multiple_entries() {
        String mhsc = """
            source "test-uid" (strict) {
                metas {
                    key1 = "val1",
                    key2 = "val2"
                }
                mh.nop := internal mh.nop {}
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        assertEquals(2, graph.metas.size());
        assertEquals("val1", graph.metas.get(0).get("key1"));
        assertEquals("val2", graph.metas.get(1).get("key2"));
    }

    @Test
    public void test_mhsc_source_metas_empty_when_absent() {
        String mhsc = "source \"test-uid\" (strict) { mh.nop := internal mh.nop {} }";
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        assertTrue(graph.metas.isEmpty());
    }

    @Test
    public void test_yaml_source_metas_populated() throws IOException {
        SourceCodeGraph graph = parseYaml("/source_code/yaml/edition-maker-5.0.26.yaml");
        assertFalse(graph.metas.isEmpty(), "edition-maker should have source-level metas");
        assertTrue(graph.metas.stream().anyMatch(m -> m.containsKey("mh.result-file-extension")),
                "Should contain mh.result-file-extension meta");
    }

    @Test
    public void test_yaml_source_metas_empty_when_absent() throws IOException {
        SourceCodeGraph graph = parseYaml("/source_code/yaml/mhdg-rg-flat-short-1.0.0.yaml");
        assertTrue(graph.metas.isEmpty());
    }

    // ============ parameterized identifiers in process codes ============

    @Test
    public void test_for_loop_parameterized_process_code() {
        String mhsc = """
            source "test-uid" (strict) {
                for L in 0 .. 2 {
                    step-{L} := internal mh.nop {}
                }
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        assertTrue(graph.processes.stream().anyMatch(p -> p.processCode.equals("step-0")));
        assertTrue(graph.processes.stream().anyMatch(p -> p.processCode.equals("step-1")));
        assertTrue(graph.processes.stream().anyMatch(p -> p.processCode.equals("step-2")));
    }

    @Test
    public void test_for_loop_parameterized_process_code_plus_offset() {
        String mhsc = """
            source "test-uid" (strict) {
                for L in 0 .. 1 {
                    step-{L+1} := internal mh.nop {}
                }
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        assertTrue(graph.processes.stream().anyMatch(p -> p.processCode.equals("step-1")));
        assertTrue(graph.processes.stream().anyMatch(p -> p.processCode.equals("step-2")));
        assertFalse(graph.processes.stream().anyMatch(p -> p.processCode.equals("step-0")));
    }

    @Test
    public void test_for_loop_parameterized_process_code_minus_offset() {
        String mhsc = """
            source "test-uid" (strict) {
                for L in 2 .. 3 {
                    step.prev{L-1} := internal mh.nop {}
                }
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        assertTrue(graph.processes.stream().anyMatch(p -> p.processCode.equals("step.prev1")));
        assertTrue(graph.processes.stream().anyMatch(p -> p.processCode.equals("step.prev2")));
    }

    @Test
    public void test_template_simple_no_parameterized_ids() {
        String mhsc = """
            source "test-uid" (strict) {
                template myStep(level) {
                    mh.nop := internal mh.nop {}
                }
                for L in 0 .. 0 {
                    @myStep(L)
                }
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        assertTrue(graph.processes.stream().anyMatch(p -> p.processCode.equals("mh.nop")));
    }

    @Test
    public void test_template_parameterized_single_iteration() {
        String mhsc = """
            source "test-uid" (strict) {
                template myStep(level) {
                    process-{level} := internal mh.nop {}
                }
                for L in 0 .. 0 {
                    @myStep(L)
                }
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        assertTrue(graph.processes.stream().anyMatch(p -> p.processCode.equals("process-0")),
                "process-0 should exist. Got: " + graph.processes.stream().map(p -> p.processCode).toList());
    }

    @Test
    public void test_template_parameterized_process_code() {
        String mhsc = """
            source "test-uid" (strict) {
                template myStep(level) {
                    process-{level} := internal mh.nop {
                        -> output{level}: ext=".txt"
                    }
                }
                for L in 0 .. 2 {
                    @myStep(L)
                }
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        assertTrue(graph.processes.stream().anyMatch(p -> p.processCode.equals("process-0")));
        assertTrue(graph.processes.stream().anyMatch(p -> p.processCode.equals("process-1")));
        assertTrue(graph.processes.stream().anyMatch(p -> p.processCode.equals("process-2")));
        // Also verify output variable names are resolved
        ExecContextParamsYaml.Process p1 = graph.processes.stream()
                .filter(p -> p.processCode.equals("process-1")).findFirst().orElseThrow();
        assertEquals("output1", p1.outputs.get(0).name);
    }

    @Test
    public void test_for_loop_parameterized_input_output_names() {
        String mhsc = """
            source "test-uid" (strict) {
                for L in 0 .. 1 {
                    store-{L} := internal mh.nop {
                        <- input{L}
                        -> result{L+1}: ext=".txt"
                    }
                }
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        ExecContextParamsYaml.Process p0 = graph.processes.stream()
                .filter(p -> p.processCode.equals("store-0")).findFirst().orElseThrow();
        assertEquals("input0", p0.inputs.get(0).name);
        assertEquals("result1", p0.outputs.get(0).name);

        ExecContextParamsYaml.Process p1 = graph.processes.stream()
                .filter(p -> p.processCode.equals("store-1")).findFirst().orElseThrow();
        assertEquals("input1", p1.inputs.get(0).name);
        assertEquals("result2", p1.outputs.get(0).name);
    }

    @Test
    public void test_for_loop_parameterized_meta_values() {
        String mhsc = """
            source "test-uid" (strict) {
                for L in 0 .. 1 {
                    eval-{L} := internal mh.nop {
                        meta varName = reqId{L}
                    }
                }
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        ExecContextParamsYaml.Process p0 = graph.processes.stream()
                .filter(p -> p.processCode.equals("eval-0")).findFirst().orElseThrow();
        assertEquals("reqId0", p0.metas.get(0).get("varName"));

        ExecContextParamsYaml.Process p1 = graph.processes.stream()
                .filter(p -> p.processCode.equals("eval-1")).findFirst().orElseThrow();
        assertEquals("reqId1", p1.metas.get(0).get("varName"));
    }

    // ============ def constant substitution in visitor ============

    @Test
    public void test_def_substitution_in_function_code() {
        String mhsc = """
            source "test-uid" (strict) {
                def call_cc_ver = "1.0.4"
                my_proc := mhdg-rg.call-cc-${call_cc_ver} {
                    timeout 60
                }
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("my_proc"))
                .findFirst().orElseThrow();
        assertEquals("mhdg-rg.call-cc-1.0.4", p.function.code,
                "def constant should be substituted in function code");
    }

    @Test
    public void test_def_substitution_multiple_defs() {
        String mhsc = """
            source "test-uid" (strict) {
                def call_cc_ver = "1.0.4"
                def read_ver = "2.0.1"
                proc1 := mhdg-rg.call-cc-${call_cc_ver} { timeout 60 }
                proc2 := mhdg-rg.read-${read_ver} { timeout 60 }
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        ExecContextParamsYaml.Process p1 = graph.processes.stream()
                .filter(p -> p.processCode.equals("proc1")).findFirst().orElseThrow();
        ExecContextParamsYaml.Process p2 = graph.processes.stream()
                .filter(p -> p.processCode.equals("proc2")).findFirst().orElseThrow();
        assertEquals("mhdg-rg.call-cc-1.0.4", p1.function.code);
        assertEquals("mhdg-rg.read-2.0.1", p2.function.code);
    }

    @Test
    public void test_def_substitution_reused_multiple_times() {
        String mhsc = """
            source "test-uid" (strict) {
                def ver = "1.0.6"
                proc1 := mhdg-rg.call-cc-${ver} { timeout 60 }
                proc2 := mhdg-rg.call-cc-${ver} {
                    timeout 60
                    sequential {
                        inner := mhdg-rg.call-cc-${ver} { timeout 30 }
                    }
                }
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        ExecContextParamsYaml.Process p1 = graph.processes.stream()
                .filter(p -> p.processCode.equals("proc1")).findFirst().orElseThrow();
        ExecContextParamsYaml.Process p2 = graph.processes.stream()
                .filter(p -> p.processCode.equals("proc2")).findFirst().orElseThrow();
        ExecContextParamsYaml.Process inner = graph.processes.stream()
                .filter(p -> p.processCode.equals("inner")).findFirst().orElseThrow();
        assertEquals("mhdg-rg.call-cc-1.0.6", p1.function.code);
        assertEquals("mhdg-rg.call-cc-1.0.6", p2.function.code);
        assertEquals("mhdg-rg.call-cc-1.0.6", inner.function.code);
    }

    @Test
    public void test_def_undefined_ref_kept_literal() {
        // If a ${name} references an undefined def, the visitor should throw or keep literal
        String mhsc = """
            source "test-uid" (strict) {
                proc1 := mhdg-rg.call-cc-${undefined_var} { timeout 60 }
            }
            """;
        assertThrows(SourceCodeGraphException.class,
                () -> SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc),
                "Undefined def reference should throw");
    }

    // ============ template parameter resolution in meta values ============

    @Test
    public void test_template_param_resolved_in_meta_string() {
        String mhsc = """
            source "test-uid" (strict) {
                template myTemplate(L) {
                    proc-{L} := internal mh.nop {
                        meta reqId = "requirementId{L}"
                        <- requirementId{L}
                        -> out{L}: ext=".txt"
                        timeout 60
                    }
                }
                mh.nop-wrapper := internal mh.nop {
                    sequential {
                        @myTemplate(0)
                    }
                }
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("proc-0"))
                .findFirst().orElseThrow();
        // Meta value should have {L} resolved to 0
        String reqIdMeta = p.metas.stream()
                .filter(m -> m.containsKey("reqId"))
                .map(m -> m.get("reqId"))
                .findFirst().orElseThrow();
        assertEquals("requirementId0", reqIdMeta,
                "Template parameter {L} should be resolved in meta string values");
        // Input should also be resolved
        assertEquals("requirementId0", p.inputs.getFirst().name);
        // Output should also be resolved
        assertEquals("out0", p.outputs.getFirst().name);
    }

    // ============ mutable modifier on output variables ============

    @Test
    public void test_mutable_modifier_on_output_variable() {
        String mhsc = """
            source "test-uid" (strict) {
                my_proc := internal mh.nop {
                    -> count: mutable, ext=".txt"
                    timeout 60
                }
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("my_proc"))
                .findFirst().orElseThrow();
        assertEquals(1, p.outputs.size());
        ExecContextParamsYaml.Variable output = p.outputs.getFirst();
        assertEquals("count", output.name);
        assertEquals(true, output.mutable);
        assertEquals(".txt", output.ext);
    }

    @Test
    public void test_no_mutable_modifier_defaults_to_null() {
        String mhsc = """
            source "test-uid" (strict) {
                my_proc := internal mh.nop {
                    -> count: ext=".txt"
                    timeout 60
                }
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("my_proc"))
                .findFirst().orElseThrow();
        ExecContextParamsYaml.Variable output = p.outputs.getFirst();
        assertEquals("count", output.name);
        assertNull(output.mutable);
    }

    @Test
    public void test_mutable_with_other_modifiers() {
        String mhsc = """
            source "test-uid" (strict) {
                my_proc := internal mh.nop {
                    -> result: type=requirement, mutable, nullable, ext=".json"
                    timeout 60
                }
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("my_proc"))
                .findFirst().orElseThrow();
        ExecContextParamsYaml.Variable output = p.outputs.getFirst();
        assertEquals("result", output.name);
        assertEquals(true, output.mutable);
        assertEquals(true, output.getNullable());
        assertEquals("requirement", output.type);
        assertEquals(".json", output.ext);
    }

    @Test
    public void test_mutable_on_source_level_output() {
        String mhsc = """
            source "test-uid" (strict) {
                variables {
                    -> outputVar: mutable, ext=".txt"
                }
                my_proc := internal mh.nop { timeout 60 }
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        assertEquals(1, graph.variables.outputs.size());
        ExecContextParamsYaml.Variable output = graph.variables.outputs.getFirst();
        assertEquals("outputVar", output.name);
        assertEquals(true, output.mutable);
    }


    // ============ Characterization test: STRING meta values with template params ============

    @Test
    public void test_string_meta_value_with_template_param_characterization() {
        String mhsc = """
            source "test-uid" (strict) {
                template myTmpl(L) {
                    my-proc-{L} := internal mh.nop {
                        meta reqId = "requirementId{L}",
                             parentId = "parentId{L}"
                        <- requirementId{L}
                        -> out{L}: ext=".txt"
                        timeout 60
                    }
                }
                wrapper := internal mh.nop {
                    sequential {
                        @myTmpl(3)
                    }
                }
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("my-proc-3"))
                .findFirst().orElseThrow();

        // Input variable name should have {L} resolved to 3
        assertEquals("requirementId3", p.inputs.getFirst().name);
        // Output variable name should have {L} resolved to 3
        assertEquals("out3", p.outputs.getFirst().name);

        // CORRECT behavior: STRING meta values should resolve {L} to the template argument
        String reqIdMetaValue = p.metas.stream()
                .filter(m -> m.containsKey("reqId"))
                .map(m -> m.get("reqId"))
                .findFirst().orElseThrow();
        assertEquals("requirementId3", reqIdMetaValue);

        String parentIdMetaValue = p.metas.stream()
                .filter(m -> m.containsKey("parentId"))
                .map(m -> m.get("parentId"))
                .findFirst().orElseThrow();
        assertEquals("parentId3", parentIdMetaValue);
    }


    @Test
    public void test_string_meta_value_with_template_param_offset() {
        String mhsc = """
            source "test-uid" (strict) {
                template objectives(L) {
                    check-{L+1} := internal mh.nop {
                        meta requirementId = "requirementId{L}",
                             hasObjectives = "hasObjectives{L+1}",
                             prevReq = "req{L-1}"
                        timeout 60
                    }
                }
                wrapper := internal mh.nop {
                    sequential {
                        @objectives(3)
                    }
                }
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        ExecContextParamsYaml.Process p = graph.processes.stream()
                .filter(proc -> proc.processCode.equals("check-4"))
                .findFirst().orElseThrow();

        String reqIdMeta = p.metas.stream()
                .filter(m -> m.containsKey("requirementId"))
                .map(m -> m.get("requirementId"))
                .findFirst().orElseThrow();
        assertEquals("requirementId3", reqIdMeta);

        String hasObjMeta = p.metas.stream()
                .filter(m -> m.containsKey("hasObjectives"))
                .map(m -> m.get("hasObjectives"))
                .findFirst().orElseThrow();
        assertEquals("hasObjectives4", hasObjMeta);

        String prevReqMeta = p.metas.stream()
                .filter(m -> m.containsKey("prevReq"))
                .map(m -> m.get("prevReq"))
                .findFirst().orElseThrow();
        assertEquals("req2", prevReqMeta);
    }

    // ============ CT: nested parallel branch leaf topology order ============

    /**
     * CT for process ordering bug in mhdg-rg-test-1.0.0.mhsc:
     * mhdg-rg.obsolete-requirement-2 and mhdg-rg.obsolete-requirement-1 must
     * connect to mhdg-rg.clear-cache in the process DAG, otherwise
     * TopologicalOrderIterator can place clear-cache before obsolete-requirement
     * in the column header.
     */
    @Test
    public void test_mhdg_rg_test_obsolete_req_connects_to_clear_cache() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mhdg-rg-test-1.0.0.mhsc");

        String dagStr = asString(graph.processGraph);

        ExecContextApiData.ProcessVertex obsoleteReq2 = findVertex(graph.processGraph, "mhdg-rg.obsolete-requirement-2");
        ExecContextApiData.ProcessVertex obsoleteReq1 = findVertex(graph.processGraph, "mhdg-rg.obsolete-requirement-1");
        ExecContextApiData.ProcessVertex clearCache = findVertex(graph.processGraph, "mhdg-rg.clear-cache");
        ExecContextApiData.ProcessVertex mhFinish = findVertex(graph.processGraph, "mh.finish");
        assertNotNull(obsoleteReq2, "mhdg-rg.obsolete-requirement-2 must exist. DAG:\n" + dagStr);
        assertNotNull(obsoleteReq1, "mhdg-rg.obsolete-requirement-1 must exist. DAG:\n" + dagStr);
        assertNotNull(clearCache, "mhdg-rg.clear-cache must exist. DAG:\n" + dagStr);
        assertNotNull(mhFinish, "mh.finish must exist. DAG:\n" + dagStr);

        boolean req2ToClearCache = graph.processGraph.getDescendants(obsoleteReq2).contains(clearCache);
        boolean req1ToClearCache = graph.processGraph.getDescendants(obsoleteReq1).contains(clearCache);

        System.out.println("DAG:\n" + dagStr);
        System.out.println("obsolete-req-2 -> clear-cache: " + req2ToClearCache);
        System.out.println("obsolete-req-1 -> clear-cache: " + req1ToClearCache);

        // DESIRED: both must connect to clear-cache
        assertTrue(req2ToClearCache, "mhdg-rg.obsolete-requirement-2 must connect to mhdg-rg.clear-cache. DAG:\n" + dagStr);
        assertTrue(req1ToClearCache, "mhdg-rg.obsolete-requirement-1 must connect to mhdg-rg.clear-cache. DAG:\n" + dagStr);
    }

    // ============ Helpers ============

    private static SourceCodeGraph parseYaml(String resourcePath) throws IOException {
        String source = IOUtils.resourceToString(resourcePath, StandardCharsets.UTF_8);
        return SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.yaml, source);
    }

    private static SourceCodeGraph parseMhsc(String resourcePath) throws IOException {
        String source = IOUtils.resourceToString(resourcePath, StandardCharsets.UTF_8);
        return SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, source);
    }
}
