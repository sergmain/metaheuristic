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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextJoin;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextSegment;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextJoinRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextSegmentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.commons.CommonConsts;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The storage invariants of a segmented ExecContext (041-EXEC-CONTEXT-SEGMENTS-PLAN, section 8.3). Every Spring-based
 * segment {@code @Test} ends with {@link #assertAll(Long)}. Each failure names the ExecContext and the segment ctx.
 *
 * <ol>
 *   <li>every Task of the ExecContext belongs to exactly one segment, and no segment references a missing Task;</li>
 *   <li>{@code LINE_CTX_ID} is unique; the root segment ({@code 1}) has no fork, and every other segment's
 *       {@code FORK_TASK_ID} is a Task of another segment of the same ExecContext;</li>
 *   <li>the derived DOT contains only the three edge kinds - within a chain, fork -> line head, line tail -> derived
 *       join ({@code SegmentAlgebra.decompose} of it gives back the stored lines, and rejects any other edge with a named
 *       code) - and is acyclic;</li>
 *   <li>per join record: finished + dead &lt;= registered &lt;= the lines resolving to that join. ⚠️ Deviation from the
 *       plan's "registered = lines resolving to it": a line born SKIPPED (PLACE_NOW graft) is never registered, so the
 *       join record of a line that never ran stays unchanged (plan section 3, section 8.6);</li>
 *   <li>(Phase 11, exact form of 4) registered = the lines resolving to that join whose {@code registered} flag is set,
 *       and every join with a registered line has a record;</li>
 *   <li>every segment's {@code STRUCTURE_HASH} equals the hash recomputed from its stored structure.</li>
 * </ol>
 */
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class SegmentInvariantAsserts {

    private final ExecContextSegmentRepository segmentRepository;
    private final ExecContextJoinRepository joinRepository;
    private final TaskRepository taskRepository;

    public void assertAll(Long execContextId) {
        final String ec = "ExecContext #" + execContextId;
        final List<ExecContextSegment> segments = segmentRepository.findIdsByExecContextId(execContextId).stream()
                .map(id -> segmentRepository.findById(id).orElseThrow())
                .sorted(Comparator.comparing((ExecContextSegment s) -> s.lineCtxId))
                .toList();
        assertFalse(segments.isEmpty(), ec + ": no segment at all");

        // 1. every Task in exactly one segment, no segment references a missing Task
        final Set<Long> taskIds = new HashSet<>(taskRepository.findAllTaskIdsByExecContextId(execContextId));
        final Map<Long, String> segmentOfTask = new HashMap<>();
        final List<SegmentData.Line> lines = new ArrayList<>();
        final Set<String> registeredCtx = new HashSet<>();
        for (ExecContextSegment s : segments) {
            s.getExecContextSegmentParams().lines.stream().filter(l -> l.registered).forEach(l -> registeredCtx.add(l.ctx));
            final List<SegmentData.Line> own = SegmentParamsConverter.lines(s.getExecContextSegmentParams());
            lines.addAll(own);
            for (SegmentData.Line line : own) {
                for (SegmentData.Vertex v : line.tasks()) {
                    final String previous = segmentOfTask.put(v.taskId(), s.lineCtxId);
                    assertNull(previous, ec + ", segment " + s.lineCtxId + ": Task #" + v.taskId() + " is also in segment " + previous);
                    assertTrue(taskIds.contains(v.taskId()), ec + ", segment " + s.lineCtxId + ": Task #" + v.taskId() + " does not exist");
                }
            }
        }
        final Set<Long> missing = new TreeSet<>(taskIds);
        missing.removeAll(segmentOfTask.keySet());
        assertEquals(Set.of(), missing, ec + ": Tasks in no segment");

        // 2. unique line ctx; root has no fork; every other fork is a Task of another segment
        final Set<String> ctxs = new HashSet<>();
        for (ExecContextSegment s : segments) {
            assertTrue(ctxs.add(s.lineCtxId), ec + ": LINE_CTX_ID " + s.lineCtxId + " is not unique");
            if (CommonConsts.TOP_LEVEL_CONTEXT_ID.equals(s.lineCtxId)) {
                assertNull(s.forkTaskId, ec + ", segment " + s.lineCtxId + ": the root segment has a fork #" + s.forkTaskId);
                continue;
            }
            assertNotNull(s.forkTaskId, ec + ", segment " + s.lineCtxId + ": a non-root segment without a fork");
            final String forkOwner = segmentOfTask.get(s.forkTaskId);
            assertNotNull(forkOwner, ec + ", segment " + s.lineCtxId + ": fork #" + s.forkTaskId + " is in no segment");
            assertNotEquals(s.lineCtxId, forkOwner, ec + ", segment " + s.lineCtxId + ": its fork #" + s.forkTaskId + " is its own Task");
        }

        // 3. derived DOT: only the three edge kinds, acyclic
        final SegmentData.Graph graph = SegmentAlgebra.toGraph(lines);
        assertEquals(new HashSet<>(lines), new HashSet<>(SegmentAlgebra.decompose(graph)),
                ec + ": the derived DOT does not decompose back into the stored lines");
        assertAcyclic(ec, graph);

        // 4. join records
        final Map<Long, Integer> resolving = new HashMap<>();
        SegmentAlgebra.derivedJoins(lines).values().forEach(join -> resolving.merge(join, 1, Integer::sum));
        final Map<Long, Integer> registeredResolving = new HashMap<>();
        SegmentAlgebra.derivedJoins(lines).forEach((ctx, join) -> {
            if (registeredCtx.contains(ctx)) {
                registeredResolving.merge(join, 1, Integer::sum);
            }
        });
        final Set<Long> withRecord = new HashSet<>();
        for (Long joinRecordId : joinRepository.findIdsByExecContextId(execContextId)) {
            final ExecContextJoin j = joinRepository.findById(joinRecordId).orElseThrow();
            withRecord.add(j.joinTaskId);
            assertEquals(registeredResolving.getOrDefault(j.joinTaskId, 0), j.linesRegistered, ec + ", join #" + j.joinTaskId
                    + ": registered differs from the flagged lines resolving to it");
            final int lineCount = resolving.getOrDefault(j.joinTaskId, 0);
            assertTrue(j.linesRegistered <= lineCount, ec + ", join #" + j.joinTaskId + ": registered " + j.linesRegistered
                    + " > lines resolving to it " + lineCount);
            assertTrue(j.linesFinished + j.linesDead <= j.linesRegistered, ec + ", join #" + j.joinTaskId + ": finished "
                    + j.linesFinished + " + dead " + j.linesDead + " > registered " + j.linesRegistered);
        }
        registeredResolving.keySet().forEach(join -> assertTrue(withRecord.contains(join),
                ec + ": join #" + join + " has registered lines but no record"));

        // 5. stored hash = recomputed hash
        for (ExecContextSegment s : segments) {
            assertEquals(SegmentStructureHash.structureHash(SegmentParamsConverter.segment(s.lineCtxId, s.forkTaskId, s.getExecContextSegmentParams())),
                    s.structureHash, ec + ", segment " + s.lineCtxId + ": STRUCTURE_HASH differs from the hash of its structure");
        }
    }

    /** Kahn's algorithm: every vertex can be removed in topological order. */
    private static void assertAcyclic(String ec, SegmentData.Graph graph) {
        final Map<Long, Integer> inDegree = new HashMap<>();
        final Map<Long, List<Long>> children = new HashMap<>();
        graph.nodes().keySet().forEach(id -> inDegree.put(id, 0));
        for (SegmentData.Edge e : graph.edges()) {
            children.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
            inDegree.merge(e.to(), 1, Integer::sum);
        }
        final Deque<Long> free = new ArrayDeque<>();
        inDegree.forEach((id, d) -> {
            if (d == 0) {
                free.add(id);
            }
        });
        int removed = 0;
        while (!free.isEmpty()) {
            final Long id = free.poll();
            removed++;
            for (Long child : children.getOrDefault(id, List.of())) {
                if (inDegree.merge(child, -1, Integer::sum) == 0) {
                    free.add(child);
                }
            }
        }
        assertEquals(inDegree.size(), removed, ec + ": the derived DOT has a cycle");
    }
}
