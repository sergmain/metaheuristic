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
import ai.metaheuristic.ai.dispatcher.beans.ExecContextJoin;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextSegment;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextJoinRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextSegmentRepository;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeInitService;
import ai.metaheuristic.ai.yaml.exec_context_segment.ExecContextSegmentParams;
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
 * Phase 7 of 041-EXEC-CONTEXT-SEGMENTS-PLAN (section 8.6): Task production writes segments and join records.
 *
 * <ul>
 *   <li>{@link #test_initialProduction_writesTheRootSegment} - initial production creates only the top-level chain,
 *       so it writes exactly the root segment; green from Phase 7.</li>
 *   <li>{@link #test_S1_run_segmentsAndJoins}, {@link #test_S6_run_segmentsAndJoins} - after a run to FINISHED: the root
 *       segment plus one segment per grafted line, and one join record per join vertex with the lines registered to it.
 *       Green once grafts (Phase 8) and the scheduler (Phase 10) are on segments. They count lines and name joins by
 *       process code, never by a grafted line's ctx, which decision 10 makes depend on allocated ids.</li>
 * </ul>
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class SegmentProductionTest extends PreparingSourceCode {

    @Autowired private PreparingSourceCodeInitService preparingSourceCodeInitService;
    @Autowired private ExecContextBaselineSupport support;
    @Autowired private ExecContextSegmentRepository segmentRepository;
    @Autowired private ExecContextJoinRepository joinRepository;
    @Autowired private SegmentInvariantAsserts invariants;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://segment-baseline-s1", EnumsApi.SourceCodeLang.mhsc, SegmentFixtureShapes.S1.mhsc());
    }

    private List<ExecContextSegment> segmentsOf(Long ecId) {
        return segmentRepository.findIdsByExecContextId(ecId).stream()
                .map(id -> segmentRepository.findById(id).orElseThrow())
                .sorted(Comparator.comparing((ExecContextSegment s) -> s.lineCtxId))
                .toList();
    }

    private List<ExecContextJoin> joinsOf(Long ecId) {
        return joinRepository.findIdsByExecContextId(ecId).stream()
                .map(id -> joinRepository.findById(id).orElseThrow())
                .toList();
    }

    private static Map<String, Long> idByProcessCode(List<ExecContextBaselineSupport.TaskRow> rows) {
        final Map<String, Long> m = new HashMap<>();
        rows.forEach(r -> m.put(r.processCode(), r.id()));
        return m;
    }

    private static void assertHashMatchesContent(ExecContextSegment s) {
        assertEquals(SegmentStructureHash.structureHash(
                        SegmentParamsConverter.segment(s.lineCtxId, s.forkTaskId, s.getExecContextSegmentParams())),
                s.structureHash, "segment " + s.lineCtxId + ": the stored hash is the hash of its stored structure");
    }

    @Test
    public void test_initialProduction_writesTheRootSegment() {
        final PreparingData.PreparingSourceCodeData data =
                preparingSourceCodeInitService.beforePreparingSourceCode(SegmentFixtureShapes.S1.mhsc(), EnumsApi.SourceCodeLang.mhsc);
        final ExecContextImpl ec = support.createAndStart(data, SegmentFixtureShapes.S1.items());
        setExecContextForTest(ec);

        final Map<String, Long> id = idByProcessCode(support.rows(ec.id));
        final List<ExecContextSegment> segments = segmentsOf(ec.id);
        assertEquals(1, segments.size(), "initial production writes one segment - the root: " + segments.stream().map(s -> s.lineCtxId).toList());

        final ExecContextSegment root = segments.getFirst();
        assertEquals("1", root.lineCtxId);
        assertNull(root.forkTaskId, "the root segment has no fork");
        final ExecContextSegmentParams p = root.getExecContextSegmentParams();
        assertEquals(1, p.lines.size(), "only the top-level chain exists before the run");
        final ExecContextSegmentParams.Line line = p.lines.getFirst();
        assertEquals("1", line.ctx);
        assertNull(line.forkTaskId);
        assertEquals(List.of(id.get("prepare"), id.get("fanout"), id.get("splitter"), id.get("post"), id.get("mh.finish")),
                line.tasks.stream().map(v -> v.taskId).toList(), "the top-level chain in chain order");
        assertEquals(List.of(id.get("post")),
                line.tasks.stream().filter(v -> "terminal".equals(v.tag)).map(v -> v.taskId).toList(), "post carries tag terminal");
        assertEquals(new TreeSet<>(line.tasks.stream().map(v -> v.taskId).toList()), new TreeSet<>(p.states.keySet()),
                "every Task of the segment has its exec state in the segment");
        assertHashMatchesContent(root);
        assertEquals(List.of(), joinsOf(ec.id), "no line has been forked yet, so no join record");
        invariants.assertAll(ec.id);
    }

    /** Runs a shape to FINISHED on the step driver. */
    private ExecContextImpl run(SegmentFixtureShapes.Shape shape) {
        final PreparingData.PreparingSourceCodeData data =
                preparingSourceCodeInitService.beforePreparingSourceCode(shape.mhsc(), EnumsApi.SourceCodeLang.mhsc);
        final ExecContextBaselineSupport.StepRun r = support.runStepByStep(() -> {
            final ExecContextImpl ec = support.createAndStart(data, shape.items());
            setExecContextForTest(ec);
            return ec.id;
        }, 80);
        assertEquals(EnumsApi.ExecContextState.FINISHED, support.state(r.execContextId()), shape.id() + ": the run must finish");
        return Objects.requireNonNull(getExecContextForTest());
    }

    /** Lines registered per join, the join named by its process code. */
    private Map<String, Integer> registeredByJoinProcess(Long ecId, Map<Long, String> processById) {
        return joinsOf(ecId).stream().collect(Collectors.toMap(j -> processById.get(j.joinTaskId), j -> j.linesRegistered, Integer::sum, TreeMap::new));
    }

    @Test
    public void test_S1_run_segmentsAndJoins() {
        final ExecContextImpl ec = run(SegmentFixtureShapes.S1);
        final List<ExecContextBaselineSupport.TaskRow> rows = support.rows(ec.id);
        final Map<Long, String> processById = rows.stream().collect(Collectors.toMap(ExecContextBaselineSupport.TaskRow::id, ExecContextBaselineSupport.TaskRow::processCode));

        final List<ExecContextSegment> segments = segmentsOf(ec.id);
        assertEquals(1 + 3, segments.size(), "S1: the root segment and one per grafted line (three items)");
        final ExecContextSegment root = segments.stream().filter(s -> "1".equals(s.lineCtxId)).findFirst().orElseThrow();
        assertEquals(Set.of("1", "1,3#0", "1,4#1"),
                root.getExecContextSegmentParams().lines.stream().map(l -> l.ctx).collect(Collectors.toSet()),
                "S1: the static parallel branches stay in the root segment");
        for (ExecContextSegment s : segments) {
            assertHashMatchesContent(s);
            if (!"1".equals(s.lineCtxId)) {
                assertEquals("splitter", processById.get(s.forkTaskId), "S1: every grafted line forks from the splitter");
                assertEquals(List.of("lineHead", "lineTail"), s.getExecContextSegmentParams().lines.getFirst().tasks.stream()
                        .map(v -> processById.get(v.taskId)).toList(), "S1: a grafted line is lineHead -> lineTail");
            }
        }
        assertEquals(Map.of("splitter", 2, "post", 3), registeredByJoinProcess(ec.id, processById),
                "S1: the two static branches join the splitter, the three grafted lines join post");
        invariants.assertAll(ec.id);
    }

    @Test
    public void test_S6_run_segmentsAndJoins() {
        final ExecContextImpl ec = run(SegmentFixtureShapes.S6);
        final Map<Long, String> processById = support.rows(ec.id).stream()
                .collect(Collectors.toMap(ExecContextBaselineSupport.TaskRow::id, ExecContextBaselineSupport.TaskRow::processCode));

        final List<ExecContextSegment> segments = segmentsOf(ec.id);
        assertEquals(1 + 2 + 4 + 8, segments.size(), "S6: the root segment and one per grafted line on three levels");
        segments.forEach(SegmentProductionTest::assertHashMatchesContent);
        assertEquals(Map.of("post", 2 + 4 + 8), registeredByJoinProcess(ec.id, processById),
                "S6: every splitter is last in its line, so all fourteen lines resolve to the one join post");
        invariants.assertAll(ec.id);
    }
}
