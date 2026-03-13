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
import org.apache.commons.io.IOUtils;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static ai.metaheuristic.commons.graph.ExecContextProcessGraphService.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for process graph edge structure produced by the .mhsc parser.
 * Validates that parallel (and) blocks only propagate direct child vertices,
 * all vertices are reachable, and mh.finish is the single leaf.
 */
@Execution(ExecutionMode.CONCURRENT)
public class TestMhscEdgeStructure {

    // ======== mhdg-rg-flat-short: single leaf (mh.finish) ========

    @Test
    public void test_short_single_leaf() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-short-1.0.0.mhsc");
        List<ExecContextApiData.ProcessVertex> leafs = findLeafs(graph);
        assertEquals(1, leafs.size(),
                "mh.finish must be the only leaf.\nLeafs: " + leafs.stream().map(v -> v.process).toList() +
                "\nGraph:\n" + asString(graph.processGraph));
        assertEquals("mh.finish", leafs.getFirst().process);
    }

    // ======== mhdg-rg-flat-short: every vertex reachable from root ========

    @Test
    public void test_short_all_vertices_reachable_from_root() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-short-1.0.0.mhsc");
        var pg = graph.processGraph;

        // Find root (vertex with no incoming edges)
        List<ExecContextApiData.ProcessVertex> roots = pg.vertexSet().stream()
                .filter(v -> pg.incomingEdgesOf(v).isEmpty())
                .toList();
        assertEquals(1, roots.size(), "Should have exactly 1 root vertex");

        // BFS from root
        Set<ExecContextApiData.ProcessVertex> visited = new HashSet<>();
        Queue<ExecContextApiData.ProcessVertex> queue = new LinkedList<>();
        queue.add(roots.getFirst());
        visited.add(roots.getFirst());
        while (!queue.isEmpty()) {
            var current = queue.poll();
            for (var edge : pg.outgoingEdgesOf(current)) {
                var target = pg.getEdgeTarget(edge);
                if (visited.add(target)) {
                    queue.add(target);
                }
            }
        }

        assertEquals(pg.vertexSet().size(), visited.size(),
                "All vertices must be reachable from root.\nUnreachable: " +
                pg.vertexSet().stream().filter(v -> !visited.contains(v)).map(v -> v.process).toList() +
                "\nGraph:\n" + asString(pg));
    }

    // ======== mhdg-rg-flat-short: every vertex can reach mh.finish ========

    @Test
    public void test_short_all_vertices_reach_finish() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-short-1.0.0.mhsc");
        var pg = graph.processGraph;

        ExecContextApiData.ProcessVertex finish = findVertex(pg, "mh.finish");
        assertNotNull(finish);

        // Reverse BFS from mh.finish
        Set<ExecContextApiData.ProcessVertex> canReachFinish = new HashSet<>();
        Queue<ExecContextApiData.ProcessVertex> queue = new LinkedList<>();
        queue.add(finish);
        canReachFinish.add(finish);
        while (!queue.isEmpty()) {
            var current = queue.poll();
            for (var edge : pg.incomingEdgesOf(current)) {
                var source = pg.getEdgeSource(edge);
                if (canReachFinish.add(source)) {
                    queue.add(source);
                }
            }
        }

        assertEquals(pg.vertexSet().size(), canReachFinish.size(),
                "All vertices must be able to reach mh.finish.\nCannot reach finish: " +
                pg.vertexSet().stream().filter(v -> !canReachFinish.contains(v)).map(v -> v.process).toList() +
                "\nGraph:\n" + asString(pg));
    }

    // ======== mhdg-rg-flat-short: parallel (and) block edges ========

    @Test
    public void test_short_amendment_gate_parallel_children() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-short-1.0.0.mhsc");
        var pg = graph.processGraph;

        // mh.nop-amendment-gate must have exactly 2 direct children: active and obsolete branches
        List<ExecContextApiData.ProcessVertex> gateChildren = findTargets(pg, "mh.nop-amendment-gate");
        Set<String> childCodes = gateChildren.stream().map(v -> v.process).collect(Collectors.toSet());
        assertTrue(childCodes.contains("mh.nop-active-branch"),
                "mh.nop-amendment-gate must have mh.nop-active-branch as child. Got: " + childCodes);
        assertTrue(childCodes.contains("mh.nop-obsolete-branch"),
                "mh.nop-amendment-gate must have mh.nop-obsolete-branch as child. Got: " + childCodes);
    }

    @Test
    public void test_short_amendment_gate2_parallel_children() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-short-1.0.0.mhsc");
        var pg = graph.processGraph;

        List<ExecContextApiData.ProcessVertex> gate2Children = findTargets(pg, "mh.nop-amendment-gate-2");
        Set<String> childCodes = gate2Children.stream().map(v -> v.process).collect(Collectors.toSet());
        assertTrue(childCodes.contains("mh.nop-active-branch-2"),
                "mh.nop-amendment-gate-2 must have mh.nop-active-branch-2 as child. Got: " + childCodes);
        assertTrue(childCodes.contains("mh.nop-obsolete-branch-2"),
                "mh.nop-amendment-gate-2 must have mh.nop-obsolete-branch-2 as child. Got: " + childCodes);
    }

    // ======== mhdg-rg-flat-short: nop-obsolete-branch must NOT receive edges from active branch internals ========

    @Test
    public void test_short_obsolete_branch_no_spurious_inbound() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-short-1.0.0.mhsc");
        var pg = graph.processGraph;

        ExecContextApiData.ProcessVertex obsoleteBranch = findVertex(pg, "mh.nop-obsolete-branch");
        assertNotNull(obsoleteBranch);

        // Only valid parent is mh.nop-amendment-gate
        Set<String> parents = pg.incomingEdgesOf(obsoleteBranch).stream()
                .map(e -> pg.getEdgeSource(e).process)
                .collect(Collectors.toSet());
        assertEquals(Set.of("mh.nop-amendment-gate"), parents,
                "mh.nop-obsolete-branch should only have mh.nop-amendment-gate as parent. Got: " + parents);
    }

    // ======== mhdg-rg-flat-short: sequential chain in batch-line-splitter-0 ========

    @Test
    public void test_short_sequential_chain_in_batch_splitter_0() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-short-1.0.0.mhsc");
        var pg = graph.processGraph;

        // store-req-0 -> set-reset-task-id-0 -> check-objectives -> nop-objectives-wrapper -> nop-amendment-gate
        assertSingleTarget(pg, "mhdg-rg.store-req-0", "mhdg-rg.set-reset-task-id-0");
        assertSingleTarget(pg, "mhdg-rg.set-reset-task-id-0", "mhdg-rg.check-objectives");
        assertSingleTarget(pg, "mhdg-rg.check-objectives", "mh.nop-objectives-wrapper");
    }

    // ======== mhdg-rg-flat-short: sequential chain in active branch ========

    @Test
    public void test_short_sequential_chain_in_active_branch() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-short-1.0.0.mhsc");
        var pg = graph.processGraph;

        // read-req-1 -> decompose-1 -> batch-line-splitter-1
        assertSingleTarget(pg, "mhdg-rg.read-req-1", "mhdg-rg.decompose-1");
        assertSingleTarget(pg, "mhdg-rg.decompose-1", "mh.batch-line-splitter-1");
    }

    // ======== mhdg-rg-flat (templated): single leaf ========

    @Test
    public void test_flat_templated_single_leaf() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-1.0.0.mhsc");
        List<ExecContextApiData.ProcessVertex> leafs = findLeafs(graph);
        assertEquals(1, leafs.size(),
                "mh.finish must be the only leaf.\nLeafs: " + leafs.stream().map(v -> v.process).toList() +
                "\nGraph:\n" + asString(graph.processGraph));
        assertEquals("mh.finish", leafs.getFirst().process);
    }

    // ======== mhdg-rg-flat (templated): all vertices reach finish ========

    @Test
    public void test_flat_templated_all_vertices_reach_finish() throws IOException {
        SourceCodeGraph graph = parseMhsc("/source_code/mhsc/mhdg-rg-flat-1.0.0.mhsc");
        var pg = graph.processGraph;

        ExecContextApiData.ProcessVertex finish = findVertex(pg, "mh.finish");
        assertNotNull(finish);

        Set<ExecContextApiData.ProcessVertex> canReachFinish = new HashSet<>();
        Queue<ExecContextApiData.ProcessVertex> queue = new LinkedList<>();
        queue.add(finish);
        canReachFinish.add(finish);
        while (!queue.isEmpty()) {
            var current = queue.poll();
            for (var edge : pg.incomingEdgesOf(current)) {
                var source = pg.getEdgeSource(edge);
                if (canReachFinish.add(source)) {
                    queue.add(source);
                }
            }
        }

        assertEquals(pg.vertexSet().size(), canReachFinish.size(),
                "All vertices must be able to reach mh.finish.\nCannot reach finish: " +
                pg.vertexSet().stream().filter(v -> !canReachFinish.contains(v)).map(v -> v.process).toList() +
                "\nGraph:\n" + asString(pg));
    }

    // ======== Simple parallel block: both branches converge to finish ========

    @Test
    public void test_simple_parallel_both_branches_reach_finish() {
        String mhsc = """
            source "test-parallel" (strict) {
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
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        var pg = graph.processGraph;

        // Single leaf must be mh.finish
        List<ExecContextApiData.ProcessVertex> leafs = findLeafs(graph);
        assertEquals(1, leafs.size(),
                "mh.finish must be the only leaf.\nLeafs: " + leafs.stream().map(v -> v.process).toList() +
                "\nGraph:\n" + asString(pg));
        assertEquals("mh.finish", leafs.getFirst().process);

        // All vertices reachable
        assertAllVerticesReachFinish(pg);
    }

    // ======== Nested parallel: deep nesting with parallel at multiple levels ========

    @Test
    public void test_nested_parallel_all_reach_finish() {
        String mhsc = """
            source "test-nested-parallel" (strict) {
                outer-gate := internal mh.nop {
                    parallel {
                        branch-active := internal mh.nop {
                            sequential {
                                step1 := internal mh.nop {}
                                inner-gate := internal mh.nop {
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
                        branch-skip := internal mh.nop {}
                    }
                }
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        var pg = graph.processGraph;

        List<ExecContextApiData.ProcessVertex> leafs = findLeafs(graph);
        assertEquals(1, leafs.size(),
                "mh.finish must be the only leaf.\nLeafs: " + leafs.stream().map(v -> v.process).toList() +
                "\nGraph:\n" + asString(pg));

        assertAllVerticesReachFinish(pg);
    }

    // ======== Parallel block: obsolete branch gets only parent as inbound ========

    @Test
    public void test_parallel_branch_no_cross_contamination() {
        String mhsc = """
            source "test-no-cross" (strict) {
                gate := internal mh.nop {
                    parallel {
                        active := internal mh.nop {
                            sequential {
                                active-child1 := internal mh.nop {}
                                active-child2 := internal mh.nop {}
                            }
                        }
                        obsolete := internal mh.nop {}
                    }
                }
            }
            """;
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        var pg = graph.processGraph;

        // 'obsolete' should only have 'gate' as parent
        ExecContextApiData.ProcessVertex obsoleteV = findVertex(pg, "obsolete");
        assertNotNull(obsoleteV);
        Set<String> parents = pg.incomingEdgesOf(obsoleteV).stream()
                .map(e -> pg.getEdgeSource(e).process)
                .collect(Collectors.toSet());
        assertEquals(Set.of("gate"), parents,
                "obsolete should only have gate as parent. Got: " + parents);
    }

    // ======== Sequential inside batch-like structure: last process edges propagate correctly ========

    @Test
    public void test_sequential_with_trailing_parallel_reaches_finish() {
        // Mimics the structure: batch-splitter { sequential { step1, step2, gate { parallel { a, b } } } }
        String mhsc = """
            source "test-seq-trail-par" (strict) {
                wrapper := internal mh.nop {
                    sequential {
                        step1 := internal mh.nop {}
                        step2 := internal mh.nop {}
                        gate := internal mh.nop {
                            parallel {
                                branch-a := internal mh.nop {
                                    sequential {
                                        a-deep := internal mh.nop {}
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
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, mhsc);
        var pg = graph.processGraph;

        List<ExecContextApiData.ProcessVertex> leafs = findLeafs(graph);
        assertEquals(1, leafs.size(),
                "mh.finish must be the only leaf.\nLeafs: " + leafs.stream().map(v -> v.process).toList() +
                "\nGraph:\n" + asString(pg));

        assertAllVerticesReachFinish(pg);

        // Verify sequential chain
        assertSingleTarget(pg, "step1", "step2");
        assertSingleTarget(pg, "step2", "gate");
    }

    // =============== Helpers ===============

    private static SourceCodeGraph parseMhsc(String resourcePath) throws IOException {
        String source = IOUtils.resourceToString(resourcePath, StandardCharsets.UTF_8);
        return SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.mhsc, source);
    }

    private static void assertSingleTarget(
            DirectedAcyclicGraph<ExecContextApiData.ProcessVertex, DefaultEdge> pg,
            String sourceProcess, String expectedTarget) {
        List<ExecContextApiData.ProcessVertex> targets = findTargets(pg, sourceProcess);
        assertEquals(1, targets.size(),
                sourceProcess + " should have exactly 1 target. Got: " +
                targets.stream().map(v -> v.process).toList());
        assertEquals(expectedTarget, targets.getFirst().process,
                sourceProcess + " target mismatch");
    }

    private static void assertAllVerticesReachFinish(
            DirectedAcyclicGraph<ExecContextApiData.ProcessVertex, DefaultEdge> pg) {
        ExecContextApiData.ProcessVertex finish = findVertex(pg, "mh.finish");
        assertNotNull(finish, "mh.finish must exist in graph");

        Set<ExecContextApiData.ProcessVertex> canReachFinish = new HashSet<>();
        Queue<ExecContextApiData.ProcessVertex> queue = new LinkedList<>();
        queue.add(finish);
        canReachFinish.add(finish);
        while (!queue.isEmpty()) {
            var current = queue.poll();
            for (var edge : pg.incomingEdgesOf(current)) {
                var source = pg.getEdgeSource(edge);
                if (canReachFinish.add(source)) {
                    queue.add(source);
                }
            }
        }

        assertEquals(pg.vertexSet().size(), canReachFinish.size(),
                "All vertices must be able to reach mh.finish.\nCannot reach finish: " +
                pg.vertexSet().stream().filter(v -> !canReachFinish.contains(v)).map(v -> v.process).toList() +
                "\nGraph:\n" + asString(pg));
    }
}
