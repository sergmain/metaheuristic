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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.SourceCodeGraph;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.graph.source_code_graph.SourceCodeGraphFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link ExecContextTaskProducingService#findEnclosingInternalFunctionContainer}
 * combined with the narrowed flag-setting condition
 * ({@code if (p.function.context == external) anyExternalFunction = true})
 * correctly classifies ExecContexts as needing (or not needing) a processor wakeup.
 *
 * The tests use the real {@link SourceCodeGraphFactory} to parse realistic YAML source codes
 * into process DAGs with realistic processContextId values, then simulate the producer loop
 * that decides whether to set anyExternalFunction.
 *
 * Scenarios:
 * 1. Pure-internal top-level (mh.nop gate + mh.finish, no external processes anywhere):
 *    flag must stay false so no websocket wakeup is fired.
 * 2. Gate with external sub-processes: flag must stay false because those sub-processes
 *    are dynamic and will be created at runtime; at production time the top-level is
 *    all internal.
 * 3. Mixed top-level (internal prelude + external worker + internal epilogue):
 *    flag must be true because there is a real external process that will need a processor.
 *
 * @author Claude
 * Date: 4/9/2026
 */
public class AnyExternalFunctionFlagTest {

    private static final String PURE_INTERNAL_YAML = """
            version: 1
            source:
              uid: pure-internal
              variables:
                globals:
                  - global-test-variable
              processes:
                - code: mh.nop
                  name: mh.nop
                  function:
                    code: mh.nop
                    context: internal
                  inputs:
                    - name: global-test-variable
                  outputs:
                    - name: splitted
                - code: mh.finish
                  name: mh.finish
                  function:
                    code: mh.finish
                    context: internal
            """;

    private static final String GATE_WITH_EXTERNAL_SUBPROCESS_YAML = """
            version: 1
            source:
              uid: gate-with-external-subprocess
              variables:
                globals:
                  - global-test-variable
              processes:
                - code: mh.nop
                  name: mh.nop
                  function:
                    code: mh.nop
                    context: internal
                  inputs:
                    - name: global-test-variable
                  outputs:
                    - name: splitted-element
                  subProcesses:
                    logic: and
                    processes:
                      - code: external-processor
                        name: external-processor
                        function:
                          code: external-processor:1.0
                          context: external
                        inputs:
                          - name: splitted-element
                        outputs:
                          - name: processed-element
                - code: mh.finish
                  name: mh.finish
                  function:
                    code: mh.finish
                    context: internal
            """;

    private static final String MIXED_TOP_LEVEL_YAML = """
            version: 1
            source:
              uid: mixed-top-level
              variables:
                globals:
                  - global-test-variable
              processes:
                - code: mh.inline-as-variable
                  name: mh.inline-as-variable
                  function:
                    code: mh.inline-as-variable
                    context: internal
                  outputs:
                    - name: inlined
                - code: assembly-raw-file
                  name: assembly-raw-file
                  function:
                    code: function-01:1.1
                    context: external
                  inputs:
                    - name: global-test-variable
                  outputs:
                    - name: assembled-raw-output
                - code: mh.finish
                  name: mh.finish
                  function:
                    code: mh.finish
                    context: internal
            """;

    /**
     * Pure-internal top-level (gate + finish). No external processes anywhere in the DAG.
     * Expectation: anyExternalFunction stays false -- no processor wakeup needed.
     */
    @Test
    public void pureInternalTopLevel_flagIsFalse() {
        boolean flag = simulateProducerLoopFlag(PURE_INTERNAL_YAML);
        assertFalse(flag,
                "A SourceCode whose top-level consists only of internal functions (mh.nop + mh.finish) " +
                "must not set anyExternalFunction");
    }

    /**
     * Gate pattern with an external sub-process under mh.nop. At production time, the external
     * sub-process is skipped via checkForInternalFunctionAsParent (its ancestor is internal)
     * and the top-level internal processes do not set the flag under the narrowed condition.
     * The sub-process task will be created dynamically when mh.nop executes, and the wakeup
     * for it will come from ExecContextTaskAssigningTopLevelService at assignment time.
     */
    @Test
    public void gateWithExternalSubProcess_flagIsFalse() {
        boolean flag = simulateProducerLoopFlag(GATE_WITH_EXTERNAL_SUBPROCESS_YAML);
        assertFalse(flag,
                "A gate with external sub-processes must not set anyExternalFunction at production time; " +
                "the sub-process tasks are created dynamically at runtime");
    }

    /**
     * Realistic mixed top-level: internal prelude, external worker, internal finish.
     * Expectation: anyExternalFunction is true because the external worker is a real
     * top-level task that will need a processor pickup.
     */
    @Test
    public void mixedTopLevel_flagIsTrue() {
        boolean flag = simulateProducerLoopFlag(MIXED_TOP_LEVEL_YAML);
        assertTrue(flag,
                "A SourceCode with at least one external top-level process must set anyExternalFunction");
    }

    /**
     * Simulates the narrowed producer-loop logic:
     *   if checkForInternalFunctionAsParent(...) != null -> skip
     *   else if p.function.context == external -> flag = true
     *
     * Uses the real SourceCodeGraphFactory and the real static helper
     * ExecContextTaskProducingService.checkForInternalFunctionAsParent, so this test will
     * stay correct even if the graph-building logic changes.
     */
    private static boolean simulateProducerLoopFlag(String yaml) {
        SourceCodeGraph scg = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.yaml, yaml);
        ExecContextParamsYaml ecpy = new ExecContextParamsYaml();
        ecpy.processes.addAll(scg.processes);

        boolean anyExternalFunction = false;
        for (ExecContextApiData.ProcessVertex v : scg.processGraph) {
            ExecContextParamsYaml.Process ancestor =
                    ExecContextTaskProducingService.findEnclosingInternalFunctionContainer(ecpy, scg.processGraph, v);
            if (ancestor != null) {
                continue;
            }
            ExecContextParamsYaml.Process self = ecpy.findProcess(v.process);
            if (self != null && self.function.context == EnumsApi.FunctionExecContext.external) {
                anyExternalFunction = true;
            }
        }
        return anyExternalFunction;
    }
}
