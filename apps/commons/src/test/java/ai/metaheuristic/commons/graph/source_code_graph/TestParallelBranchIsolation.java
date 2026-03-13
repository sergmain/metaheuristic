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
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.*;
import java.util.stream.Collectors;

import static ai.metaheuristic.commons.graph.ExecContextProcessGraphService.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that parallel (and) branches are truly independent.
 * In parallel mode each subProcess is a single computational entity.
 * The return set from a parallel block should contain only the true leaves
 * of each branch - not the branch vertices themselves, not the parent.
 *
 * Correct convergence: after-gate's parents = {leaf of branch-a, leaf of branch-b}
 * Bug: after-gate's parents = {branch-a, branch-b, gate, a-deep, ...} - all internals leak.
 */
@Execution(ExecutionMode.CONCURRENT)
public class TestParallelBranchIsolation {

    /**
     * gate { parallel { active(seq{a1,a2}), obsolete(seq{ob1}) } } -> after-gate
     *
     * Expected edges:
     *   gate -> active-branch -> a1 -> a2
     *   gate -> obsolete-branch -> ob1
     *   {a2, ob1} -> after-gate -> mh.finish
     *
     * Bug: after-gate gets parents {a2, ob1, active-branch, obsolete-branch, gate}
     */
    @Test
    public void test_mhsc_process_after_parallel_gate_has_only_leaf_parents() {
        String mhsc = """
            source "test-post-parallel" (strict) {
                wrapper := internal mh.nop {
                    sequential {
                        step1 := internal mh.nop {}
                        gate := internal mh.nop {
                            parallel {
                                active-branch := internal mh.nop {
                                    sequential {
                                        a1 := internal mh.nop {}
                                        a2 := internal mh.nop {}
                                    }
                                }
                                obsolete-branch := internal mh.nop {
                                    sequential {
                                        ob1 := internal mh.nop {}
                                    }
                                }
                            }
                        }
                        after-gate := internal mh.nop {}
                    }
                }
            }
            """;

        var graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        var pg = graph.processGraph;

        System.out.println("MHSC Graph:\n" + asString(pg));

        // All vertices must reach mh.finish
        assertAllVerticesReachFinish(pg);

        // Each parallel branch starts from gate only
        assertDirectParents(pg, "active-branch", Set.of("gate"));
        assertDirectParents(pg, "obsolete-branch", Set.of("gate"));

        // after-gate should converge from the leaves of each branch only
        ExecContextApiData.ProcessVertex afterGate = findVertex(pg, "after-gate");
        assertNotNull(afterGate);
        Set<String> afterGateParents = directParentNames(pg, afterGate);

        // Must NOT include non-leaf internals
        assertFalse(afterGateParents.contains("gate"),
                "gate must NOT be a direct parent of after-gate. Parents: " + afterGateParents);
        assertFalse(afterGateParents.contains("active-branch"),
                "active-branch must NOT be a direct parent of after-gate. Parents: " + afterGateParents);
        assertFalse(afterGateParents.contains("obsolete-branch"),
                "obsolete-branch must NOT be a direct parent of after-gate. Parents: " + afterGateParents);
    }

    /**
     * No after-gate - parallel branches converge directly to mh.finish.
     *
     * Expected edges:
     *   wrapper -> step1 -> gate -> active-branch -> a1
     *   gate -> obsolete-branch
     *   {a1, obsolete-branch} -> mh.finish
     *
     * mh.finish must NOT have gate or active-branch as direct parents.
     */
    @Test
    public void test_mhsc_parallel_leaves_converge_directly_to_finish() {
        String mhsc = """
            source "test-parallel-converge-finish" (strict) {
                wrapper := internal mh.nop {
                    sequential {
                        step1 := internal mh.nop {}
                        gate := internal mh.nop {
                            parallel {
                                active-branch := internal mh.nop {
                                    sequential {
                                        a1 := internal mh.nop {}
                                    }
                                }
                                obsolete-branch := internal mh.nop {}
                            }
                        }
                    }
                }
            }
            """;

        var graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        var pg = graph.processGraph;

        System.out.println("MHSC converge-to-finish Graph:\n" + asString(pg));

        assertAllVerticesReachFinish(pg);

        // Parallel branches start from gate
        assertDirectParents(pg, "active-branch", Set.of("gate"));
        assertDirectParents(pg, "obsolete-branch", Set.of("gate"));

        // mh.finish direct parents: only the true leaves of each parallel branch
        // a1 is the leaf of active-branch's sequential chain
        // obsolete-branch has no subprocesses, so it IS its own leaf
        ExecContextApiData.ProcessVertex finish = findVertex(pg, "mh.finish");
        assertNotNull(finish);
        Set<String> finishParents = directParentNames(pg, finish);

        assertTrue(finishParents.contains("a1"),
                "a1 (leaf of active-branch) must be a direct parent of mh.finish. Parents: " + finishParents);
        assertTrue(finishParents.contains("obsolete-branch"),
                "obsolete-branch (leaf of itself) must be a direct parent of mh.finish. Parents: " + finishParents);
        assertFalse(finishParents.contains("gate"),
                "gate must NOT be a direct parent of mh.finish. Parents: " + finishParents);
        assertFalse(finishParents.contains("active-branch"),
                "active-branch must NOT be a direct parent of mh.finish. Parents: " + finishParents);

        // obsolete-branch must NOT have any ancestor from inside active-branch
        ExecContextApiData.ProcessVertex obsoleteV = findVertex(pg, "obsolete-branch");
        assertNotNull(obsoleteV);
        Set<String> obsoleteAncestors = pg.getAncestors(obsoleteV).stream()
                .map(v -> v.process).collect(Collectors.toSet());
        assertFalse(obsoleteAncestors.contains("a1"),
                "a1 must NOT be an ancestor of obsolete-branch");
        assertFalse(obsoleteAncestors.contains("active-branch"),
                "active-branch must NOT be an ancestor of obsolete-branch");
    }

    /**
     * Deeper parallel: both branches have sequential children, converge to mh.finish.
     *
     * Expected edges:
     *   gate -> active-branch -> a1 -> a2
     *   gate -> obsolete-branch -> ob1
     *   {a2, ob1} -> mh.finish
     */
    @Test
    public void test_mhsc_deep_parallel_converge_to_finish() {
        String mhsc = """
            source "test-deep-parallel-finish" (strict) {
                gate := internal mh.nop {
                    parallel {
                        active-branch := internal mh.nop {
                            sequential {
                                a1 := internal mh.nop {}
                                a2 := internal mh.nop {}
                            }
                        }
                        obsolete-branch := internal mh.nop {
                            sequential {
                                ob1 := internal mh.nop {}
                            }
                        }
                    }
                }
            }
            """;

        var graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        var pg = graph.processGraph;

        System.out.println("MHSC deep-parallel-finish Graph:\n" + asString(pg));

        assertAllVerticesReachFinish(pg);

        assertDirectParents(pg, "active-branch", Set.of("gate"));
        assertDirectParents(pg, "obsolete-branch", Set.of("gate"));

        // mh.finish direct parents: only a2 and ob1 (true leaves)
        ExecContextApiData.ProcessVertex finish = findVertex(pg, "mh.finish");
        assertNotNull(finish);
        Set<String> finishParents = directParentNames(pg, finish);

        assertTrue(finishParents.contains("a2"),
                "a2 must be a direct parent of mh.finish. Parents: " + finishParents);
        assertTrue(finishParents.contains("ob1"),
                "ob1 must be a direct parent of mh.finish. Parents: " + finishParents);
        assertFalse(finishParents.contains("gate"),
                "gate must NOT be a direct parent of mh.finish. Parents: " + finishParents);
        assertFalse(finishParents.contains("active-branch"),
                "active-branch must NOT be a direct parent of mh.finish. Parents: " + finishParents);
        assertFalse(finishParents.contains("obsolete-branch"),
                "obsolete-branch must NOT be a direct parent of mh.finish. Parents: " + finishParents);
    }

    /**
     * Parallel branch where one child has NO subprocesses (leaf nop).
     * That child itself IS the leaf - it should appear in the convergence set.
     */
    @Test
    public void test_mhsc_parallel_leaf_child_is_leaf() {
        String mhsc = """
            source "test-parallel-leaf-child" (strict) {
                wrapper := internal mh.nop {
                    sequential {
                        gate := internal mh.nop {
                            parallel {
                                deep-branch := internal mh.nop {
                                    sequential {
                                        deep1 := internal mh.nop {}
                                    }
                                }
                                leaf-branch := internal mh.nop {}
                            }
                        }
                        after-gate := internal mh.nop {}
                    }
                }
            }
            """;

        var graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        var pg = graph.processGraph;

        System.out.println("MHSC leaf-child Graph:\n" + asString(pg));

        assertAllVerticesReachFinish(pg);

        // after-gate's parents: deep1 (leaf of deep-branch) and leaf-branch (itself is a leaf)
        ExecContextApiData.ProcessVertex afterGate = findVertex(pg, "after-gate");
        assertNotNull(afterGate);
        Set<String> parents = directParentNames(pg, afterGate);
        assertTrue(parents.contains("deep1"),
                "deep1 must be a parent of after-gate. Parents: " + parents);
        assertTrue(parents.contains("leaf-branch"),
                "leaf-branch must be a parent of after-gate (it is its own leaf). Parents: " + parents);
        assertFalse(parents.contains("gate"),
                "gate must NOT be a direct parent of after-gate. Parents: " + parents);
        assertFalse(parents.contains("deep-branch"),
                "deep-branch must NOT be a direct parent of after-gate. Parents: " + parents);
    }

    /**
     * YAML equivalent: parallel branches converge directly to mh.finish.
     */
    @Test
    public void test_yaml_parallel_converge_to_finish() {
        String yaml = """
            version: 5
            source:
              uid: test-yaml-parallel-finish
              processes:
                - code: gate
                  function:
                    code: mh.nop
                    context: internal
                  subProcesses:
                    logic: and
                    processes:
                      - code: active-branch
                        function:
                          code: mh.nop
                          context: internal
                        subProcesses:
                          logic: sequential
                          processes:
                            - code: a1
                              function:
                                code: mh.nop
                                context: internal
                            - code: a2
                              function:
                                code: mh.nop
                                context: internal
                      - code: obsolete-branch
                        function:
                          code: mh.nop
                          context: internal
                        subProcesses:
                          logic: sequential
                          processes:
                            - code: ob1
                              function:
                                code: mh.nop
                                context: internal
            """;

        var graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.yaml, yaml);
        var pg = graph.processGraph;

        System.out.println("YAML converge-to-finish Graph:\n" + asString(pg));

        assertAllVerticesReachFinish(pg);

        assertDirectParents(pg, "active-branch", Set.of("gate"));
        assertDirectParents(pg, "obsolete-branch", Set.of("gate"));

        ExecContextApiData.ProcessVertex finish = findVertex(pg, "mh.finish");
        assertNotNull(finish);
        Set<String> finishParents = directParentNames(pg, finish);

        assertTrue(finishParents.contains("a2"),
                "a2 must be a direct parent of mh.finish (YAML). Parents: " + finishParents);
        assertTrue(finishParents.contains("ob1"),
                "ob1 must be a direct parent of mh.finish (YAML). Parents: " + finishParents);
        assertFalse(finishParents.contains("gate"),
                "gate must NOT be a direct parent of mh.finish (YAML). Parents: " + finishParents);
        assertFalse(finishParents.contains("active-branch"),
                "active-branch must NOT be a direct parent of mh.finish (YAML). Parents: " + finishParents);
        assertFalse(finishParents.contains("obsolete-branch"),
                "obsolete-branch must NOT be a direct parent of mh.finish (YAML). Parents: " + finishParents);
    }

    /**
     * YAML equivalent with after-gate process.
     */
    @Test
    public void test_yaml_parallel_branches_isolated_with_after_gate() {
        String yaml = """
            version: 5
            source:
              uid: test-yaml-parallel-isolation
              processes:
                - code: wrapper
                  function:
                    code: mh.nop
                    context: internal
                  subProcesses:
                    logic: sequential
                    processes:
                      - code: step1
                        function:
                          code: mh.nop
                          context: internal
                      - code: gate
                        function:
                          code: mh.nop
                          context: internal
                        subProcesses:
                          logic: and
                          processes:
                            - code: active-branch
                              function:
                                code: mh.nop
                                context: internal
                              subProcesses:
                                logic: sequential
                                processes:
                                  - code: a1
                                    function:
                                      code: mh.nop
                                      context: internal
                                  - code: a2
                                    function:
                                      code: mh.nop
                                      context: internal
                            - code: obsolete-branch
                              function:
                                code: mh.nop
                                context: internal
                              subProcesses:
                                logic: sequential
                                processes:
                                  - code: ob1
                                    function:
                                      code: mh.nop
                                      context: internal
                      - code: after-gate
                        function:
                          code: mh.nop
                          context: internal
            """;

        var graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.yaml, yaml);
        var pg = graph.processGraph;

        System.out.println("YAML with-after-gate Graph:\n" + asString(pg));

        assertAllVerticesReachFinish(pg);

        assertDirectParents(pg, "active-branch", Set.of("gate"));
        assertDirectParents(pg, "obsolete-branch", Set.of("gate"));

        ExecContextApiData.ProcessVertex afterGate = findVertex(pg, "after-gate");
        assertNotNull(afterGate);
        Set<String> parents = directParentNames(pg, afterGate);
        assertFalse(parents.contains("gate"),
                "gate must NOT be a direct parent of after-gate (YAML). Parents: " + parents);
        assertFalse(parents.contains("active-branch"),
                "active-branch must NOT be a direct parent of after-gate (YAML). Parents: " + parents);
        assertFalse(parents.contains("obsolete-branch"),
                "obsolete-branch must NOT be a direct parent of after-gate (YAML). Parents: " + parents);
    }

    // ======== Helpers ========

    private static Set<String> directParentNames(
            DirectedAcyclicGraph<ExecContextApiData.ProcessVertex, DefaultEdge> pg,
            ExecContextApiData.ProcessVertex v) {
        return pg.incomingEdgesOf(v).stream()
                .map(pg::getEdgeSource)
                .map(vv -> vv.process)
                .collect(Collectors.toSet());
    }

    private static void assertDirectParents(
            DirectedAcyclicGraph<ExecContextApiData.ProcessVertex, DefaultEdge> pg,
            String processCode, Set<String> expectedParents) {
        ExecContextApiData.ProcessVertex v = findVertex(pg, processCode);
        assertNotNull(v, "Process '" + processCode + "' must exist in graph");
        Set<String> actualParents = directParentNames(pg, v);
        assertEquals(expectedParents, actualParents,
                "Process '" + processCode + "' has wrong parents.\nGraph:\n" + asString(pg));
    }

    private static void assertAllVerticesReachFinish(
            DirectedAcyclicGraph<ExecContextApiData.ProcessVertex, DefaultEdge> pg) {
        ExecContextApiData.ProcessVertex finish = findVertex(pg, "mh.finish");
        assertNotNull(finish, "mh.finish must exist in graph");

        Set<ExecContextApiData.ProcessVertex> reachable = new HashSet<>();
        Queue<ExecContextApiData.ProcessVertex> queue = new LinkedList<>();
        queue.add(finish);
        reachable.add(finish);
        while (!queue.isEmpty()) {
            var current = queue.poll();
            for (var edge : pg.incomingEdgesOf(current)) {
                var source = pg.getEdgeSource(edge);
                if (reachable.add(source)) {
                    queue.add(source);
                }
            }
        }

        assertEquals(pg.vertexSet().size(), reachable.size(),
                "All vertices must reach mh.finish.\nCannot reach: " +
                pg.vertexSet().stream().filter(v -> !reachable.contains(v)).map(v -> v.process).toList() +
                "\nGraph:\n" + asString(pg));
    }
}
