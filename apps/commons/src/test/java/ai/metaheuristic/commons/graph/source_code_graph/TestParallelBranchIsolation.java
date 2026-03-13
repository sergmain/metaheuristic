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
 * Tests that parallel (and) branch VERTICES do not leak into the return set
 * of processSubProcessChildren. In parallel mode, each subProcess is a single
 * computational entity. The direct child process vertices (active-branch,
 * obsolete-branch) must NOT appear as parents of the next process outside
 * the parallel block. Only the true recursive leaves (allAndLastProcesses)
 * and the parent vertex itself should propagate.
 */
@Execution(ExecutionMode.CONCURRENT)
public class TestParallelBranchIsolation {

    /**
     * gate { parallel { active(seq{a1,a2}), obsolete(seq{ob1}) } } -> mh.finish
     *
     * mh.finish parents must include: gate (parentVertex), a2, ob1 (allAndLastProcesses)
     * mh.finish parents must NOT include: active-branch, obsolete-branch (andProcesses)
     */
    @Test
    public void test_mhsc_parallel_branch_vertices_dont_leak_to_finish() {
        String mhsc = """
            source "test-parallel-no-leak" (strict) {
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

        System.out.println("MHSC Graph:\n" + asString(pg));

        assertAllVerticesReachFinish(pg);
        assertDirectParents(pg, "active-branch", Set.of("gate"));
        assertDirectParents(pg, "obsolete-branch", Set.of("gate"));

        ExecContextApiData.ProcessVertex finish = findVertex(pg, "mh.finish");
        assertNotNull(finish);
        Set<String> finishParents = directParentNames(pg, finish);

        // parentVertex and allAndLastProcesses ARE parents
        assertTrue(finishParents.contains("gate"), "gate must be parent of mh.finish. Parents: " + finishParents);
        assertTrue(finishParents.contains("a2"), "a2 must be parent of mh.finish. Parents: " + finishParents);
        assertTrue(finishParents.contains("ob1"), "ob1 must be parent of mh.finish. Parents: " + finishParents);

        // andProcesses (branch vertices) must NOT be parents
        assertFalse(finishParents.contains("active-branch"),
                "active-branch must NOT be parent of mh.finish. Parents: " + finishParents);
        assertFalse(finishParents.contains("obsolete-branch"),
                "obsolete-branch must NOT be parent of mh.finish. Parents: " + finishParents);
    }

    /**
     * Same but with after-gate process in the outer sequential chain.
     */
    @Test
    public void test_mhsc_parallel_branch_vertices_dont_leak_to_after_gate() {
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

        System.out.println("MHSC after-gate Graph:\n" + asString(pg));

        assertAllVerticesReachFinish(pg);

        ExecContextApiData.ProcessVertex afterGate = findVertex(pg, "after-gate");
        assertNotNull(afterGate);
        Set<String> parents = directParentNames(pg, afterGate);

        // andProcesses must NOT be parents of after-gate
        assertFalse(parents.contains("active-branch"),
                "active-branch must NOT be parent of after-gate. Parents: " + parents);
        assertFalse(parents.contains("obsolete-branch"),
                "obsolete-branch must NOT be parent of after-gate. Parents: " + parents);
    }

    /**
     * Leaf branch (no subprocesses) — the branch vertex itself IS the leaf.
     * It must still appear as a parent of the next process.
     */
    @Test
    public void test_mhsc_leaf_branch_is_its_own_leaf() {
        String mhsc = """
            source "test-leaf-branch" (strict) {
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

        System.out.println("MHSC leaf-branch Graph:\n" + asString(pg));

        assertAllVerticesReachFinish(pg);

        ExecContextApiData.ProcessVertex afterGate = findVertex(pg, "after-gate");
        assertNotNull(afterGate);
        Set<String> parents = directParentNames(pg, afterGate);

        // leaf-branch IS its own leaf — must be a parent of after-gate
        assertTrue(parents.contains("leaf-branch"),
                "leaf-branch must be parent of after-gate. Parents: " + parents);
        assertTrue(parents.contains("deep1"),
                "deep1 must be parent of after-gate. Parents: " + parents);
        // deep-branch has subprocesses — must NOT leak
        assertFalse(parents.contains("deep-branch"),
                "deep-branch must NOT be parent of after-gate. Parents: " + parents);
    }

    /**
     * YAML equivalent.
     */
    @Test
    public void test_yaml_parallel_branch_vertices_dont_leak() {
        String yaml = """
            version: 5
            source:
              uid: test-yaml-parallel-no-leak
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

        System.out.println("YAML Graph:\n" + asString(pg));

        assertAllVerticesReachFinish(pg);

        ExecContextApiData.ProcessVertex finish = findVertex(pg, "mh.finish");
        assertNotNull(finish);
        Set<String> finishParents = directParentNames(pg, finish);

        assertTrue(finishParents.contains("gate"), "gate must be parent of mh.finish (YAML). Parents: " + finishParents);
        assertTrue(finishParents.contains("a2"), "a2 must be parent of mh.finish (YAML). Parents: " + finishParents);
        assertTrue(finishParents.contains("ob1"), "ob1 must be parent of mh.finish (YAML). Parents: " + finishParents);

        assertFalse(finishParents.contains("active-branch"),
                "active-branch must NOT be parent of mh.finish (YAML). Parents: " + finishParents);
        assertFalse(finishParents.contains("obsolete-branch"),
                "obsolete-branch must NOT be parent of mh.finish (YAML). Parents: " + finishParents);
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
        assertNotNull(v, "Process '" + processCode + "' must exist");
        assertEquals(expectedParents, directParentNames(pg, v),
                "Process '" + processCode + "' has wrong parents.\nGraph:\n" + asString(pg));
    }

    private static void assertAllVerticesReachFinish(
            DirectedAcyclicGraph<ExecContextApiData.ProcessVertex, DefaultEdge> pg) {
        ExecContextApiData.ProcessVertex finish = findVertex(pg, "mh.finish");
        assertNotNull(finish, "mh.finish must exist");

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
