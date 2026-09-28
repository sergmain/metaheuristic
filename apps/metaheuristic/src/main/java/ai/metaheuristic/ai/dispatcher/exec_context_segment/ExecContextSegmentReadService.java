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
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextJoinRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextSegmentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.yaml.exec_context_segment.ExecContextSegmentParams;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
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
 *   <li>{@link #graph}, {@link #dot}, {@link #verifyGraph}, {@link #allTasksTopologically} - the whole-graph views,
 *       derived from every line (Phase 12, decision 13: no DOT is stored); {@link #tasksByCtx} - the Tasks of given
 *       ctxs, reading only the segments owning them.</li>
 * </ul>
 *
 * <p>Error code prefix: {@code 01.919.} (unique to this class).
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ExecContextSegmentReadService {

    /** Segments read per query when every segment of an ExecContext is needed. */
    public static final int PAGE = 500;

    private final ExecContextSegmentRepository segmentRepository;
    private final ExecContextSegmentTxService segmentTxService;
    private final TaskRepository taskRepository;
    private final ExecContextJoinRepository joinRepository;

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

    /** The graph the segments imply (vertices: every Task with ctx and tag; edges: the three line-based kinds); empty for no segments. */
    public SegmentData.Graph graph(Long execContextId) {
        final Snapshot s = snapshot(execContextId);
        return s.lines().isEmpty() ? new SegmentData.Graph(Map.of(), Set.of()) : SegmentAlgebra.toGraph(s.lines());
    }

    /** The graph as DOT in the format the whole-ExecContext graph stored (vertex attributes {@code ctxid}, {@code tag}). */
    public String dot(Long execContextId) {
        return SegmentDotUtils.toDot(graph(execContextId));
    }

    /** Why the ExecContext's segments are not a valid line-based structure, or null when they are. */
    @Nullable
    public String structureError(Long execContextId) {
        return SegmentAlgebra.structureError(snapshot(execContextId).lines());
    }

    /** True when the segments form a valid line-based structure - the segment counterpart of the whole-graph {@code verifyGraph}. */
    public boolean verifyGraph(Long execContextId) {
        final String error = structureError(execContextId);
        if (error != null) {
            log.warn("01.919.040 ExecContext #{} has an invalid segment structure: {}", execContextId, error);
            return false;
        }
        return true;
    }

    /** Every Task with its stored state, in {@link SegmentAlgebra#topologicalOrder} - the counterpart of {@code getAllTasksTopologically}. */
    public List<TaskData.TaskWithState> allTasksTopologically(Long execContextId) {
        final Snapshot s = snapshot(execContextId);
        if (s.lines().isEmpty()) {
            return List.of();
        }
        final List<TaskData.TaskWithState> out = new ArrayList<>();
        for (Long id : SegmentAlgebra.topologicalOrder(SegmentAlgebra.toGraph(s.lines()))) {
            out.add(new TaskData.TaskWithState(id, s.states().getOrDefault(id, EnumsApi.TaskExecState.NONE)));
        }
        return out;
    }

    /**
     * The Tasks of each given ctx with their stored states, by ctx; a ctx no line has is absent - the counterpart of
     * {@code findVerticesByTaskContextIds}. A ctx is one line, so only the segments owning the ctxs are read.
     */
    public Map<String, List<TaskData.TaskWithState>> tasksByCtx(Long execContextId, Collection<String> ctxs) {
        final SegmentLineView view = lineView(execContextId);
        final Map<String, List<TaskData.TaskWithState>> out = new HashMap<>();
        for (String ctx : ctxs) {
            final SegmentData.Line line = view.lineOfCtx(ctx);
            if (line == null) {
                continue;
            }
            final Map<Long, EnumsApi.TaskExecState> states = view.segmentOfLine(ctx).getExecContextSegmentParams().states;
            final List<TaskData.TaskWithState> tasks = new ArrayList<>(line.tasks().size());
            for (SegmentData.Vertex v : line.tasks()) {
                tasks.add(new TaskData.TaskWithState(v.taskId(), states.getOrDefault(v.taskId(), EnumsApi.TaskExecState.NONE)));
            }
            out.put(ctx, tasks);
        }
        return out;
    }

    /** Every variable-state entry as stored, read in pages of {@value #PAGE} segments, sorted by Task id. */
    private List<ExecContextApiData.VariableState> storedVariableStates(Long execContextId) {
        final List<Long> ids = segmentRepository.findIdsByExecContextId(execContextId);
        final List<ExecContextApiData.VariableState> entries = new ArrayList<>();
        for (int from = 0; from < ids.size(); from += PAGE) {
            for (ExecContextSegment s : segmentRepository.findAllById(ids.subList(from, Math.min(from + PAGE, ids.size())))) {
                entries.addAll(s.getExecContextSegmentParams().variableStates);
            }
        }
        entries.sort(Comparator.comparing(e -> e.taskId));
        return entries;
    }

    /**
     * Every variable-state entry of the ExecContext, sorted by Task id, input flags derived by
     * {@link SegmentVariableStates#withDerivedInputs} - what the whole-ExecContext variable-state record showed.
     */
    public List<ExecContextApiData.VariableState> variableStates(Long execContextId) {
        return SegmentVariableStates.withDerivedInputs(storedVariableStates(execContextId));
    }

    /**
     * The {@code ext} recorded for variable {@code variableId} by its producer's output entry, or null. The producer's
     * entry sits in the segment owning the variable's ctx {@code variableCtx}, read first; every segment is read only
     * when it is not there.
     */
    @Nullable
    public String outputExt(Long execContextId, String variableCtx, Long variableId) {
        final ExecContextSegment owner = segmentTxService.findSegmentOfCtx(execContextId, variableCtx);
        if (owner != null) {
            final String ext = SegmentVariableStates.outputExt(owner.getExecContextSegmentParams().variableStates, variableId);
            if (ext != null) {
                return ext;
            }
        }
        return SegmentVariableStates.outputExt(storedVariableStates(execContextId), variableId);
    }

    /**
     * A text for the state page's change detection - the part the whole-ExecContext records' versions used to be: count
     * and {@code VERSION} sum of the segment and join records. Every write raises one {@code VERSION}, every addition
     * raises a count, every removal lowers one, so the text changes between two reads unless removals and additions in
     * between cancel out exactly in both count and sum. Two aggregate queries per table, no segment is loaded.
     */
    public String changeVersion(Long execContextId) {
        final Long segmentSum = segmentRepository.sumVersionByExecContextId(execContextId);
        final Long joinSum = joinRepository.sumVersionByExecContextId(execContextId);
        return segmentRepository.countByExecContextId(execContextId) + "," + (segmentSum == null ? 0L : segmentSum)
                + "," + joinRepository.countByExecContextId(execContextId) + "," + (joinSum == null ? 0L : joinSum);
    }
}
