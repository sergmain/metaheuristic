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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextSegment;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextSegmentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.yaml.exec_context_segment.ExecContextSegmentParams;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Reads of an ExecContext's segments (041-EXEC-CONTEXT-SEGMENTS-PLAN, Phase 10): what the scheduler, the readiness check
 * and reconciliation read instead of the whole-ExecContext graph and task-state records.
 *
 * <ul>
 *   <li>{@link #snapshot} - every line and every Task state, read in pages of {@value #PAGE} segments (decision 3: no
 *       unbounded read). Readiness today includes a global rule - the leaf is ready only when nothing else is - so the
 *       ready set needs every line.</li>
 *   <li>{@link #findAllForAssigning} - the ready set of {@link SegmentStates#ready}, proven equal to the whole-graph
 *       {@code findAllForAssigning} over seeded runs (Phase 4 goldens).</li>
 *   <li>{@link #lineView} - lines on demand, for readers that need a Task's neighbours only.</li>
 * </ul>
 *
 * <p>Error code prefix: {@code 01.919.} (unique to this class).
 */
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ExecContextSegmentReadService {

    /** Segments read per query when every segment of an ExecContext is needed. */
    public static final int PAGE = 500;

    private final ExecContextSegmentRepository segmentRepository;
    private final ExecContextSegmentTxService segmentTxService;
    private final TaskRepository taskRepository;

    /** Every line of an ExecContext and the stored state of every Task (a Task without one is NONE). */
    public record Snapshot(List<SegmentData.Line> lines, Map<Long, EnumsApi.TaskExecState> states) {}

    public Snapshot snapshot(Long execContextId) {
        final List<Long> ids = segmentRepository.findIdsByExecContextId(execContextId);
        final List<SegmentData.Line> lines = new ArrayList<>();
        final Map<Long, EnumsApi.TaskExecState> states = new HashMap<>();
        for (int from = 0; from < ids.size(); from += PAGE) {
            for (ExecContextSegment s : segmentRepository.findAllById(ids.subList(from, Math.min(from + PAGE, ids.size())))) {
                final ExecContextSegmentParams p = s.getExecContextSegmentParams();
                lines.addAll(SegmentParamsConverter.lines(p));
                states.putAll(p.states);
            }
        }
        return new Snapshot(lines, states);
    }

    /** The Tasks ready to be handed out - the segment counterpart of the whole-graph {@code findAllForAssigning}. */
    public List<ExecContextData.TaskVertex> findAllForAssigning(Long execContextId, boolean includeForCaching) {
        final Snapshot s = snapshot(execContextId);
        if (s.lines().isEmpty()) {
            return List.of();
        }
        final SegmentStates.Adjacency adj = SegmentStates.Adjacency.of(s.lines());
        final Set<Long> ready = SegmentStates.ready(adj, s.lines(), SegmentStates.stateOf(s.states()), includeForCaching);
        final List<ExecContextData.TaskVertex> out = new ArrayList<>(ready.size());
        for (Long id : ready) {
            final SegmentData.Line line = adj.index().lineOf(id);
            final SegmentData.Vertex v = Objects.requireNonNull(adj.vertices().get(id), () -> "01.919.020 Task #" + id + " has no vertex");
            out.add(new ExecContextData.TaskVertex(id, line.ctx(), v.tag()));
        }
        return out;
    }

    /** A {@link SegmentLineView} over this ExecContext's segments, for one call. */
    public SegmentLineView lineView(Long execContextId) {
        return new SegmentLineView(execContextId,
                ctx -> segmentTxService.findSegmentOfCtx(execContextId, ctx),
                taskId -> ctxOf(execContextId, taskId),
                fork -> segmentRepository.findByExecContextIdAndForkTaskId(execContextId, fork));
    }

    /**
     * Every Task reachable from {@code taskId} (not including it), with its ctx and tag - the segment counterpart of the
     * whole-graph {@code findDescendants} (Phase 11, reset). Reads every line, as the graph walk did.
     */
    public List<ExecContextData.TaskVertex> descendants(Long execContextId, Long taskId) {
        final Snapshot s = snapshot(execContextId);
        if (s.lines().isEmpty()) {
            return List.of();
        }
        final SegmentStates.Adjacency adj = SegmentStates.Adjacency.of(s.lines());
        final List<ExecContextData.TaskVertex> out = new ArrayList<>();
        for (Long id : SegmentStates.descendants(adj, taskId)) {
            final SegmentData.Vertex v = Objects.requireNonNull(adj.vertices().get(id), () -> "01.919.030 Task #" + id + " has no vertex");
            out.add(new ExecContextData.TaskVertex(id, adj.index().lineOf(id).ctx(), v.tag()));
        }
        return out;
    }

    private String ctxOf(Long execContextId, Long taskId) {
        final TaskImpl t = taskRepository.findByIdReadOnly(taskId);
        if (t == null) {
            throw new IllegalStateException("01.919.010 Task #" + taskId + " not found, ExecContext #" + execContextId);
        }
        return t.getTaskParamsYaml().task.taskContextId;
    }
}
