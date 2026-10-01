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

package ai.metaheuristic.ai.dispatcher.exec_context_segment;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeInitService;
import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 041-EXEC-CONTEXT-SEGMENTS-PLAN Phase 12 (section 8.6, decision 13): the whole-graph reads are derived from segments.
 *
 * <p>A FINISHED run of the S1-shaped SourceCode (13 Tasks) is read back through {@link ExecContextSegmentReadService}:
 * the derived DOT (what {@code mh_get_exec_context_graph} returns), the structure check behind {@code verifyGraph}, the
 * topological Task list and the per-ctx Task lists. Vertices are named {@code processCode@ctx} through {@code MH_TASK},
 * so the expectation holds modulo Task ids.
 *
 * <p>⚠️ Section 8.6 compares with "the S1 golden"; that golden is the DOT of RG ExecContext 325 (26 Tasks), not of this
 * fixture. The expected edges here are derived by hand from the fixture's shape and its Phase 1 pins instead: the root
 * chain {@code prepare -> fanout -> splitter -> post -> mh.finish}; {@code fanout} forks the static branches
 * {@code branchA@1,3#0} and {@code branchB@1,4#1}, whose join is {@code splitter}; {@code splitter} forks the three
 * grafted lines {@code lineHead -> lineTail} at {@code 1,2#1..3}, whose join is {@code post}.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class SegmentDotViewEndpointTest extends PreparingSourceCode {

    @Autowired private PreparingSourceCodeInitService preparingSourceCodeInitService;
    @Autowired private ExecContextBaselineSupport support;
    @Autowired private ExecContextSegmentReadService segmentReadService;
    @Autowired private SegmentInvariantAsserts invariants;

    private static final String S1_EDGES = """
            branchA@1,3#0 -> splitter@1
            branchB@1,4#1 -> splitter@1
            fanout@1 -> branchA@1,3#0
            fanout@1 -> branchB@1,4#1
            fanout@1 -> splitter@1
            lineHead@1,2#1 -> lineTail@1,2#1
            lineHead@1,2#2 -> lineTail@1,2#2
            lineHead@1,2#3 -> lineTail@1,2#3
            lineTail@1,2#1 -> post@1
            lineTail@1,2#2 -> post@1
            lineTail@1,2#3 -> post@1
            post@1 -> mh.finish@1
            prepare@1 -> fanout@1
            splitter@1 -> lineHead@1,2#1
            splitter@1 -> lineHead@1,2#2
            splitter@1 -> lineHead@1,2#3
            splitter@1 -> post@1""";

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://segment-baseline-s1", EnumsApi.SourceCodeLang.mhsc, SegmentFixtureShapes.S1.mhsc());
    }

    private Long runS1() {
        final PreparingData.PreparingSourceCodeData data =
                preparingSourceCodeInitService.beforePreparingSourceCode(SegmentFixtureShapes.S1.mhsc(), EnumsApi.SourceCodeLang.mhsc);
        final ExecContextBaselineSupport.StepRun r = support.runStepByStep(() -> {
            final ExecContextImpl ec = support.createAndStart(data, SegmentFixtureShapes.S1.items());
            setExecContextForTest(ec);
            return ec.id;
        }, 80);
        assertEquals(EnumsApi.ExecContextState.FINISHED, support.state(r.execContextId()), "S1: the run must finish");
        return r.execContextId();
    }

    @Test
    public void test_S1_derivedDot_verifyGraph_topologicalAndPerCtxReads() {
        final Long ecId = runS1();
        final List<ExecContextBaselineSupport.TaskRow> rows = support.rows(ecId);
        final Map<Long, String> keyById = rows.stream().collect(Collectors.toMap(ExecContextBaselineSupport.TaskRow::id, ExecContextBaselineSupport.TaskRow::key));
        final Map<Long, String> ctxById = rows.stream().collect(Collectors.toMap(ExecContextBaselineSupport.TaskRow::id, ExecContextBaselineSupport.TaskRow::ctx));

        // the derived DOT: every Task once, its ctxid equal to MH_TASK's, the join keeps tag terminal, the hand-derived edges
        final SegmentData.Graph dot = SegmentDotUtils.parse(segmentReadService.dot(ecId));
        assertEquals(new TreeSet<>(keyById.keySet()), new TreeSet<>(dot.nodes().keySet()), "S1: the derived DOT has exactly the Tasks of MH_TASK");
        for (SegmentData.Node n : dot.nodes().values()) {
            assertEquals(ctxById.get(n.taskId()), n.ctx(), "S1: Task " + keyById.get(n.taskId()) + " - ctxid in the DOT equals MH_TASK's taskContextId");
        }
        assertEquals(List.of("post@1"), dot.nodes().values().stream().filter(n -> "terminal".equals(n.tag()))
                .map(n -> keyById.get(n.taskId())).toList(), "S1: only the join post carries tag terminal");
        final String edges = dot.edges().stream()
                .map(e -> keyById.get(e.from()) + " -> " + keyById.get(e.to()))
                .sorted()
                .collect(Collectors.joining("\n"));
        assertEquals(S1_EDGES, edges, "S1: the derived DOT's edges, by processCode@ctx");

        // verifyGraph on segments
        assertNull(segmentReadService.structureError(ecId), "S1: the segments are a valid structure");
        assertTrue(segmentReadService.verifyGraph(ecId), "S1: verifyGraph on segments");

        // every Task, topologically, with its state: all OK after the run; every DOT edge points forward
        final List<TaskData.TaskWithState> topo = segmentReadService.allTasksTopologically(ecId);
        assertEquals(rows.size(), topo.size(), "S1: every Task exactly once in the topological list");
        final Map<Long, Integer> pos = new HashMap<>();
        for (int i = 0; i < topo.size(); i++) {
            pos.put(topo.get(i).taskId, i);
            assertEquals(EnumsApi.TaskExecState.OK, topo.get(i).state, "S1: Task " + keyById.get(topo.get(i).taskId) + " is OK in its segment");
        }
        for (SegmentData.Edge e : dot.edges()) {
            assertTrue(pos.get(e.from()) < pos.get(e.to()), "S1: " + keyById.get(e.from()) + " before " + keyById.get(e.to()));
        }

        // Tasks per ctx: the root chain in chain order, one grafted line, nothing for a ctx no line has
        final Map<String, List<TaskData.TaskWithState>> byCtx = segmentReadService.tasksByCtx(ecId, List.of("1", "1,2#2", "1,2#99"));
        assertEquals(Set.of("1", "1,2#2"), byCtx.keySet(), "S1: only ctxs that are lines");
        assertEquals(List.of("prepare@1", "fanout@1", "splitter@1", "post@1", "mh.finish@1"),
                byCtx.get("1").stream().map(t -> keyById.get(t.taskId)).toList(), "S1: the root line in chain order");
        assertEquals(List.of("lineHead@1,2#2", "lineTail@1,2#2"),
                byCtx.get("1,2#2").stream().map(t -> keyById.get(t.taskId)).toList(), "S1: the grafted line 1,2#2 in chain order");
        assertTrue(byCtx.values().stream().flatMap(List::stream).allMatch(t -> t.state == EnumsApi.TaskExecState.OK),
                "S1: per-ctx states come from the segments - all OK");

        invariants.assertAll(ecId);
    }
}
