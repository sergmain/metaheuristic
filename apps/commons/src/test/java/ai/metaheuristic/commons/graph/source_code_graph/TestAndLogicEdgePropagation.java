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
 * Tests that parallel (and) logic in process graphs correctly propagates
 * recursive leaves from ALL children — not just the last one.
 *
 * The bug: processSubProcessChildren overwrote tempLastProcesses each iteration,
 * so deep leaves from earlier 'and' children were lost and never connected downstream.
 */
@Execution(ExecutionMode.CONCURRENT)
public class TestAndLogicEdgePropagation {

    // ======== MHSC: simple parallel — both branches' deep leaves reach mh.finish ========

    @Test
    public void test_mhsc_parallel_both_deep_branches_reach_finish() {
        String mhsc = """
            source "test-and-deep" (strict) {
                gate := internal mh.nop {
                    parallel {
                        branch-a := internal mh.nop {
                            sequential {
                                deep-a1 := internal mh.nop {}
                                deep-a2 := internal mh.nop {}
                            }
                        }
                        branch-b := internal mh.nop {
                            sequential {
                                deep-b1 := internal mh.nop {}
                            }
                        }
                    }
                }
            }
            """;
        //act
        var graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);

        assertSingleLeafIsFinish(graph);
        assertAllVerticesReachFinish(graph.processGraph);
    }

    // ======== MHSC: nested parallel at multiple levels ========

    @Test
    public void test_mhsc_nested_parallel_all_reach_finish() {
        String mhsc = """
            source "test-nested-and" (strict) {
                outer := internal mh.nop {
                    parallel {
                        active := internal mh.nop {
                            sequential {
                                step1 := internal mh.nop {}
                                inner := internal mh.nop {
                                    parallel {
                                        inner-a := internal mh.nop {}
                                        inner-b := internal mh.nop {
                                            sequential {
                                                inner-b-child := internal mh.nop {}
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        skip := internal mh.nop {}
                    }
                }
            }
            """;
        //act
        var graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);

        assertSingleLeafIsFinish(graph);
        assertAllVerticesReachFinish(graph.processGraph);
    }

    // ======== MHSC: parallel child with no subprocesses (leaf nop) is not orphaned ========

    @Test
    public void test_mhsc_parallel_leaf_nop_reaches_finish() {
        String mhsc = """
            source "test-and-leaf" (strict) {
                gate := internal mh.nop {
                    parallel {
                        heavy := internal mh.nop {
                            sequential {
                                child1 := internal mh.nop {}
                                child2 := internal mh.nop {}
                            }
                        }
                        light := internal mh.nop {}
                    }
                }
            }
            """;
        //act
        var graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);

        assertSingleLeafIsFinish(graph);
        assertAllVerticesReachFinish(graph.processGraph);
    }

    // ======== MHSC: sequential-inside-parallel ending with another parallel ========
    // Mimics mhdg-rg structure: batch-splitter { seq { ..., gate { parallel { active{deep...}, obsolete } } } }

    @Test
    public void test_mhsc_seq_trailing_parallel_reaches_finish() {
        String mhsc = """
            source "test-seq-trail-and" (strict) {
                wrapper := internal mh.nop {
                    sequential {
                        step1 := internal mh.nop {}
                        gate := internal mh.nop {
                            parallel {
                                branch-a := internal mh.nop {
                                    sequential {
                                        a-deep1 := internal mh.nop {}
                                        a-deep2 := internal mh.nop {}
                                    }
                                }
                                branch-b := internal mh.nop {
                                    sequential {
                                        b-deep := internal mh.nop {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
            """;
        //act
        var graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);

        assertSingleLeafIsFinish(graph);
        assertAllVerticesReachFinish(graph.processGraph);
    }

    // ======== YAML: parallel with deep branches — same bug existed in YAML builder ========

    @Test
    public void test_yaml_parallel_deep_branches_reach_finish() {
        String yaml = """
            version: 5
            source:
              uid: test-yaml-and-deep
              processes:
                - code: gate
                  function:
                    code: mh.nop
                    context: internal
                  subProcesses:
                    logic: and
                    processes:
                      - code: branch-a
                        function:
                          code: mh.nop
                          context: internal
                        subProcesses:
                          logic: sequential
                          processes:
                            - code: deep-a1
                              function:
                                code: mh.nop
                                context: internal
                            - code: deep-a2
                              function:
                                code: mh.nop
                                context: internal
                      - code: branch-b
                        function:
                          code: mh.nop
                          context: internal
                        subProcesses:
                          logic: sequential
                          processes:
                            - code: deep-b1
                              function:
                                code: mh.nop
                                context: internal
            """;
        //act
        var graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.yaml, yaml);

        assertSingleLeafIsFinish(graph);
        assertAllVerticesReachFinish(graph.processGraph);
    }

    // ======== YAML: nested parallel ========

    @Test
    public void test_yaml_nested_parallel_reach_finish() {
        String yaml = """
            version: 5
            source:
              uid: test-yaml-nested-and
              processes:
                - code: outer
                  function:
                    code: mh.nop
                    context: internal
                  subProcesses:
                    logic: and
                    processes:
                      - code: active
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
                            - code: inner
                              function:
                                code: mh.nop
                                context: internal
                              subProcesses:
                                logic: and
                                processes:
                                  - code: inner-a
                                    function:
                                      code: mh.nop
                                      context: internal
                                  - code: inner-b
                                    function:
                                      code: mh.nop
                                      context: internal
                                    subProcesses:
                                      logic: sequential
                                      processes:
                                        - code: inner-b-child
                                          function:
                                            code: mh.nop
                                            context: internal
                      - code: skip
                        function:
                          code: mh.nop
                          context: internal
            """;
        //act
        var graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.yaml, yaml);

        assertSingleLeafIsFinish(graph);
        assertAllVerticesReachFinish(graph.processGraph);
    }

    // ======== Helpers ========

    private static void assertSingleLeafIsFinish(SourceCodeGraph graph) {
        List<ExecContextApiData.ProcessVertex> leafs = findLeafs(graph);
        assertEquals(1, leafs.size(),
                "mh.finish must be the only leaf.\nLeafs: " + leafs.stream().map(v -> v.process).toList() +
                "\nGraph:\n" + asString(graph.processGraph));
        assertEquals("mh.finish", leafs.getFirst().process);
    }

    private static void assertAllVerticesReachFinish(
            DirectedAcyclicGraph<ExecContextApiData.ProcessVertex, DefaultEdge> pg) {
        ExecContextApiData.ProcessVertex finish = findVertex(pg, "mh.finish");
        assertNotNull(finish, "mh.finish must exist in graph");

        // Reverse BFS from mh.finish
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
