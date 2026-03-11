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
import ai.metaheuristic.ai.exceptions.SourceCodeGraphException;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
        SourceCodeGraph mhscGraph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhscSource);

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
        ExecContextData.ProcessVertex eval1 = findVertex(graph.processGraph, "mh.evaluation1");
        assertNotNull(eval1);
        List<ExecContextData.ProcessVertex> afterEval1 = findTargets(graph.processGraph, eval1.process);
        assertEquals(1, afterEval1.size());
        assertEquals("mh.multiply_1.2", afterEval1.get(0).process);

        List<ExecContextData.ProcessVertex> afterMultiply = findTargets(graph.processGraph, "mh.multiply_1.2");
        assertEquals(1, afterMultiply.size());
        assertEquals("mh.exec-source-code", afterMultiply.get(0).process);

        List<ExecContextData.ProcessVertex> afterExec = findTargets(graph.processGraph, "mh.exec-source-code");
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
        ExecContextData.ProcessVertex trafaret = findVertex(graph.processGraph, "trafaret");
        assertNotNull(trafaret);
        List<ExecContextData.ProcessVertex> afterTrafaret = findTargets(graph.processGraph, "trafaret");
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
