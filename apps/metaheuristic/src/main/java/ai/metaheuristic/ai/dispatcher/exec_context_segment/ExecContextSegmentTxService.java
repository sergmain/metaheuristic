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
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextJoinRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextSegmentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.exec_context_segment.ExecContextSegmentParams;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.utils.ContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * The one writer of ExecContext segments and join records (041-EXEC-CONTEXT-SEGMENTS-PLAN, Phase 7): Task production
 * writes here instead of the whole-ExecContext graph and task-state records.
 *
 * <ul>
 *   <li>{@link #addTasks} - a new Task whose parent is the tail of the line at its ctx is appended to that line; a Task
 *       at a ctx that has no line yet starts one, forked from its single parent. A new line goes into its OWN segment,
 *       or - {@link SegmentStart#ENCLOSING} - into the segment that owns its fork's line (decision 7: grafted and
 *       splitter lines start a segment, static sub-block lines do not; the creating code path says which). A Task with
 *       no parent starts the root line, in the root segment ({@code 1}).</li>
 *   <li>{@link #registerLines} - replaces wiring the tails of new lines into their join: the join is derived from the
 *       lines (never stored as an edge), and the join record counts the lines registered to it.</li>
 * </ul>
 *
 * <p>The segment that owns a ctx is found by walking the ctx up ({@code ContextUtils.deriveParentTaskContextId}) to the
 * nearest segment start - a fork's line ctx is the parent ctx of the lines it forks - ending at the root segment.
 *
 * <p>Grafts (Phase 8): {@link SegmentStart#own(Long)} makes the new line's segment take an id allocated before its Tasks
 * were built (decision 10: the line ctx is derived from that id); {@link #lineTaskIds}, {@link #addVariableStates} and
 * {@link #markLineSkipped} read and write only the grafted line's own segment.
 *
 * <p>Error code prefix: {@code 01.913.} (unique to this class).
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ExecContextSegmentTxService {

    // 041 Phase 8: SegmentStart.own(id) is an own segment whose id was allocated in advance - a graft whose line ctx is
    // derived from that id (decision 10). ENCLOSING and OWN keep their meaning.
    /** Where a new line goes: its own new segment, or the segment that owns its fork's line. */
    public sealed interface SegmentStart {
        record Enclosing() implements SegmentStart {}

        /** @param presetSegmentId the id the new segment takes; null - allocate one when the segment is created */
        record Own(@Nullable Long presetSegmentId) implements SegmentStart {}

        SegmentStart ENCLOSING = new Enclosing();
        SegmentStart OWN = new Own(null);

        static SegmentStart own(Long presetSegmentId) {
            return new Own(presetSegmentId);
        }
    }

    private final ExecContextSegmentRepository segmentRepository;
    private final ExecContextJoinRepository joinRepository;
    private final ExecContextSegmentIdService idService;
    private final TaskRepository taskRepository;

    /**
     * Adds newly created Tasks, each with the same parents - the segment counterpart of adding vertices and edges to the
     * whole-ExecContext graph and setting their state.
     */
    @Transactional
    public void addTasks(Long execContextId, List<Long> parentTaskIds, List<TaskApiData.TaskWithContext> tasks,
                         EnumsApi.TaskExecState state, @Nullable String tag, SegmentStart start) {
        TxUtils.checkTxExists();
        for (TaskApiData.TaskWithContext t : tasks) {
            addTask(execContextId, parentTaskIds, t.taskId, t.taskContextId, state, tag, start);
        }
    }

    private void addTask(Long execContextId, List<Long> parentTaskIds, Long taskId, String ctx,
                         EnumsApi.TaskExecState state, @Nullable String tag, SegmentStart start) {
        final ExecContextSegment owning = findSegmentOfCtx(execContextId, ctx);
        if (owning != null) {
            final ExecContextSegmentParams p = owning.getExecContextSegmentParams();
            final ExecContextSegmentParams.Line line = lineAt(p, ctx);
            if (line != null) {
                final Long tail = line.tasks.getLast().taskId;
                // 041 Phase 22: besides the line's tail, the parents may be tails of lines that now resolve to this Task -
                // the process after one whose sub-processes are produced with it (an external function's sub-process
                // block): the process graph wires those tails to it, the segments derive that edge
                if (!parentTaskIds.contains(tail)) {
                    throw new IllegalStateException("01.913.020 Task #" + taskId + " at ctx " + ctx + " of ExecContext #"
                            + execContextId + " must follow the line's tail #" + tail + ", its parents are " + parentTaskIds);
                }
                line.tasks.add(new ExecContextSegmentParams.Vertex(taskId, tag));
                p.states.put(taskId, state);
                save(owning, p);
                registerJoinedLines(execContextId, taskId, parentTaskIds.stream().filter(id -> !tail.equals(id)).distinct().toList());
                return;
            }
        }

        if (parentTaskIds.isEmpty()) {
            if (!CommonConsts.TOP_LEVEL_CONTEXT_ID.equals(ctx)) {
                throw new IllegalStateException("01.913.030 Task #" + taskId + " has no parent, so it starts the root line, "
                        + "but its ctx is " + ctx + ", ExecContext #" + execContextId);
            }
            createSegment(execContextId, ctx, null, taskId, tag, state, null);
            return;
        }
        if (parentTaskIds.size() != 1) {
            throw new IllegalStateException("01.913.040 Task #" + taskId + " starts line " + ctx + " of ExecContext #" + execContextId
                    + ", a line has exactly one fork, parents " + parentTaskIds);
        }
        final Long fork = parentTaskIds.getFirst();
        if (start instanceof SegmentStart.Own own) {
            createSegment(execContextId, ctx, fork, taskId, tag, state, own.presetSegmentId());
            return;
        }
        final String forkCtx = ctxOf(fork);
        final ExecContextSegment owner = findSegmentOfCtx(execContextId, forkCtx);
        if (owner == null) {
            throw new IllegalStateException("01.913.050 no segment holds fork #" + fork + " (ctx " + forkCtx + ") of line " + ctx
                    + ", ExecContext #" + execContextId);
        }
        final ExecContextSegmentParams p = owner.getExecContextSegmentParams();
        final ExecContextSegmentParams.Line forkLine = lineAt(p, forkCtx);
        if (forkLine == null || forkLine.tasks.stream().noneMatch(v -> fork.equals(v.taskId))) {
            throw new IllegalStateException("01.913.055 segment " + owner.lineCtxId + " does not hold fork #" + fork + " at ctx " + forkCtx
                    + ", ExecContext #" + execContextId);
        }
        final ExecContextSegmentParams.Line line = new ExecContextSegmentParams.Line(ctx, fork);
        line.tasks.add(new ExecContextSegmentParams.Vertex(taskId, tag));
        p.lines.add(line);
        p.states.put(taskId, state);
        save(owner, p);
    }

    /**
     * Registers new lines with their joins: for every tail in {@code lastIds}, the derived join of the tail's line gets
     * its record's registered count raised by one (the record is created when absent).
     */
    @Transactional
    public void registerLines(Long execContextId, List<Long> lastIds) {
        TxUtils.checkTxExists();
        final Map<Long, Integer> perJoin = new TreeMap<>();
        for (Long tail : lastIds) {
            final String ctx = ctxOf(tail);
            final ExecContextSegment seg = findSegmentOfCtx(execContextId, ctx);
            final ExecContextSegmentParams.Line line = seg == null ? null : lineAt(seg.getExecContextSegmentParams(), ctx);
            if (line == null || !tail.equals(line.tasks.getLast().taskId)) {
                throw new IllegalStateException("01.913.060 Task #" + tail + " is not the tail of a line at ctx " + ctx
                        + ", ExecContext #" + execContextId);
            }
            final Long join = derivedJoin(execContextId, line);
            if (join == null) {
                throw new IllegalStateException("01.913.070 line " + ctx + " of ExecContext #" + execContextId + " has no join");
            }
            // 041 Phase 11: the line knows it is counted, so a reset reviving a never-registered line can tell the two apart
            // - and a line already counted is not counted twice
            if (line.registered) {
                continue;
            }
            perJoin.merge(join, 1, Integer::sum);
            line.registered = true;
            save(seg, seg.getExecContextSegmentParams());
        }
        perJoin.forEach((joinTaskId, n) -> {
            ExecContextJoin j = joinRepository.findByExecContextIdAndJoinTaskId(execContextId, joinTaskId);
            if (j == null) {
                j = new ExecContextJoin();
                j.execContextId = execContextId;
                j.joinTaskId = joinTaskId;
                j.createdOn = System.currentTimeMillis();
            }
            j.linesRegistered += n;
            joinRepository.save(j);
        });
    }

    /**
     * 041 Phase 22: {@code lineTails} are the parents of the just appended {@code joinTaskId} other than its chain
     * predecessor. Each must be the tail of a line whose derived join is now {@code joinTaskId}; those lines are
     * registered with it - as the code creating a sub-block registers the lines it forked ({@link #registerLines}).
     */
    private void registerJoinedLines(Long execContextId, Long joinTaskId, List<Long> lineTails) {
        if (lineTails.isEmpty()) {
            return;
        }
        for (Long t : lineTails) {
            final String ctx = ctxOf(t);
            final ExecContextSegment seg = findSegmentOfCtx(execContextId, ctx);
            final ExecContextSegmentParams.Line line = seg == null ? null : lineAt(seg.getExecContextSegmentParams(), ctx);
            if (line == null || !t.equals(line.tasks.getLast().taskId) || !joinTaskId.equals(derivedJoin(execContextId, line))) {
                throw new IllegalStateException("01.913.025 Task #" + joinTaskId + " of ExecContext #" + execContextId + " has Task #" + t
                        + " (ctx " + ctx + ") as a parent, which is neither its chain predecessor nor the tail of a line joining it");
            }
        }
        registerLines(execContextId, lineTails);
    }

    /**
     * The derived join of {@code line}: the Task after its fork in the fork's line, or - when the fork is that line's last
     * Task - the derived join of the fork's line. Null for the root line.
     */
    @Nullable
    public Long derivedJoin(Long execContextId, ExecContextSegmentParams.Line line) {
        ExecContextSegmentParams.Line current = line;
        while (true) {
            final Long fork = current.forkTaskId;
            if (fork == null) {
                return null;
            }
            final String forkCtx = ctxOf(fork);
            final ExecContextSegment seg = findSegmentOfCtx(execContextId, forkCtx);
            final ExecContextSegmentParams.Line forkLine = seg == null ? null : lineAt(seg.getExecContextSegmentParams(), forkCtx);
            if (forkLine == null) {
                throw new IllegalStateException("01.913.080 fork #" + fork + " at ctx " + forkCtx + " is in no line, ExecContext #" + execContextId);
            }
            for (int i = 0; i < forkLine.tasks.size(); i++) {
                if (fork.equals(forkLine.tasks.get(i).taskId)) {
                    if (i + 1 < forkLine.tasks.size()) {
                        return forkLine.tasks.get(i + 1).taskId;
                    }
                    break;
                }
            }
            current = forkLine;
        }
    }

    /** The segment owning {@code ctx}: the nearest segment start walking the ctx up; null when there is none yet. */
    @Nullable
    public ExecContextSegment findSegmentOfCtx(Long execContextId, String ctx) {
        String c = ctx;
        while (c != null) {
            final ExecContextSegment s = segmentRepository.findByExecContextIdAndLineCtxId(execContextId, c);
            if (s != null) {
                return s;
            }
            c = ContextUtils.deriveParentTaskContextId(c);
        }
        return null;
    }

    private static ExecContextSegmentParams.@Nullable Line lineAt(ExecContextSegmentParams p, String ctx) {
        for (ExecContextSegmentParams.Line l : p.lines) {
            if (ctx.equals(l.ctx)) {
                return l;
            }
        }
        return null;
    }

    private void createSegment(Long execContextId, String ctx, @Nullable Long fork, Long taskId, @Nullable String tag,
                               EnumsApi.TaskExecState state, @Nullable Long presetSegmentId) {
        final ExecContextSegment s = new ExecContextSegment();
        s.id = presetSegmentId != null ? presetSegmentId : idService.allocate();
        s.execContextId = execContextId;
        s.lineCtxId = ctx;
        s.forkTaskId = fork;
        s.createdOn = System.currentTimeMillis();
        final ExecContextSegmentParams p = new ExecContextSegmentParams();
        final ExecContextSegmentParams.Line line = new ExecContextSegmentParams.Line(ctx, fork);
        line.tasks.add(new ExecContextSegmentParams.Vertex(taskId, tag));
        p.lines.add(line);
        p.states.put(taskId, state);
        save(s, p);
    }

    /** The Task ids of the line at {@code lineCtxId}, in chain order. */
    @Transactional(readOnly = true)
    public List<Long> lineTaskIds(Long execContextId, String lineCtxId) {
        return new ArrayList<>(requireLine(execContextId, lineCtxId).line().tasks.stream().map(v -> v.taskId).toList());
    }

    /** Appends variable-state entries to the segment owning {@code ctx} - the segment counterpart of registering them. */
    @Transactional
    public void addVariableStates(Long execContextId, String ctx, List<ExecContextApiData.VariableState> states) {
        TxUtils.checkTxExists();
        final ExecContextSegment s = findSegmentOfCtx(execContextId, ctx);
        if (s == null) {
            throw new IllegalStateException("01.913.100 no segment owns ctx " + ctx + ", ExecContext #" + execContextId);
        }
        final ExecContextSegmentParams p = s.getExecContextSegmentParams();
        p.variableStates.addAll(states);
        save(s, p);
    }

    /**
     * Marks {@code headTaskId} and every Task after it in the line at {@code lineCtxId} SKIPPED, in that line's segment
     * only, and returns their ids in chain order. For a freshly grafted line this is the whole SKIPPED closure: a line's
     * join always has a live parent (its fork, or the enclosing fork), so the kill stops at the line's tail.
     */
    @Transactional
    public List<Long> markLineSkipped(Long execContextId, String lineCtxId, Long headTaskId) {
        TxUtils.checkTxExists();
        final OwnedLine owned = requireLine(execContextId, lineCtxId);
        final List<Long> ids = owned.line().tasks.stream().map(v -> v.taskId).toList();
        final int from = ids.indexOf(headTaskId);
        if (from < 0) {
            throw new IllegalStateException("01.913.120 Task #" + headTaskId + " is not in line " + lineCtxId + ", ExecContext #" + execContextId);
        }
        final List<Long> skipped = new ArrayList<>(ids.subList(from, ids.size()));
        final ExecContextSegmentParams p = owned.segment().getExecContextSegmentParams();
        skipped.forEach(id -> p.states.put(id, EnumsApi.TaskExecState.SKIPPED));
        save(owned.segment(), p);
        return skipped;
    }

    private record OwnedLine(ExecContextSegment segment, ExecContextSegmentParams.Line line) {}

    private OwnedLine requireLine(Long execContextId, String lineCtxId) {
        final ExecContextSegment s = findSegmentOfCtx(execContextId, lineCtxId);
        final ExecContextSegmentParams.Line line = s == null ? null : lineAt(s.getExecContextSegmentParams(), lineCtxId);
        if (s == null || line == null) {
            throw new IllegalStateException("01.913.110 no line at ctx " + lineCtxId + ", ExecContext #" + execContextId);
        }
        return new OwnedLine(s, line);
    }

    /** Writes params and the structure hash recomputed from them. */
    private void save(ExecContextSegment s, ExecContextSegmentParams p) {
        s.updateParams(p);
        s.structureHash = SegmentStructureHash.structureHash(SegmentParamsConverter.segment(s.lineCtxId, s.forkTaskId, p));
        segmentRepository.save(s);
    }

    /**
     * Removes whole lines from the segments (041 Phase 11: a reset deleting a dynamic splitter's old lines, which the
     * splitter re-creates when it re-runs). {@code taskIds} must hold every Task of each line it touches. A segment left
     * without lines is deleted; a trimmed one is written with its structure hash recomputed. Each removed REGISTERED line
     * leaves its derived join's record - registered, and finished / dead by its tail's state - unless that join is
     * removed too; the records of removed join Tasks are deleted. The removed Tasks' states, tries and variable-state
     * entries go with their lines.
     */
    @Transactional
    public void removeLines(Long execContextId, Set<Long> taskIds) {
        TxUtils.checkTxExists();
        if (taskIds.isEmpty()) {
            return;
        }
        final SegmentLineView view = new SegmentLineView(execContextId, ctx -> findSegmentOfCtx(execContextId, ctx), this::ctxOf,
                fork -> segmentRepository.findByExecContextIdAndForkTaskId(execContextId, fork));
        final Map<String, SegmentData.Line> removed = new TreeMap<>();
        for (Long t : taskIds) {
            final SegmentData.Line line = view.lineOf(t);
            if (line.isRoot()) {
                throw new IllegalStateException("01.913.140 the root line cannot be removed, Task #" + t + ", ExecContext #" + execContextId);
            }
            removed.putIfAbsent(line.ctx(), line);
        }
        for (SegmentData.Line line : removed.values()) {
            for (SegmentData.Vertex v : line.tasks()) {
                if (!taskIds.contains(v.taskId())) {
                    throw new IllegalStateException("01.913.150 line " + line.ctx() + " is removed only in part: Task #" + v.taskId()
                            + " stays, ExecContext #" + execContextId);
                }
            }
        }

        // join shares, computed while every line still resolves
        final Map<Long, int[]> delta = new TreeMap<>();
        for (SegmentData.Line line : removed.values()) {
            if (!view.paramsLine(line.ctx()).registered) {
                continue;
            }
            final Long join = SegmentAlgebra.derivedJoin(view::lineOf, line);
            if (join == null || taskIds.contains(join)) {
                continue;
            }
            final EnumsApi.TaskExecState tail = view.segmentOfLine(line.ctx()).getExecContextSegmentParams().states
                    .getOrDefault(line.tail().taskId(), EnumsApi.TaskExecState.NONE);
            final int[] d = delta.computeIfAbsent(join, k -> new int[3]);
            d[0]++;
            if (tail == EnumsApi.TaskExecState.OK) {
                d[1]++;
            }
            else if (SegmentStates.dead(tail)) {
                d[2]++;
            }
        }

        // structure
        final Map<Long, ExecContextSegment> touched = new LinkedHashMap<>();
        removed.keySet().forEach(ctx -> {
            final ExecContextSegment s = view.segmentOfLine(ctx);
            touched.put(s.id, s);
        });
        for (ExecContextSegment s : touched.values()) {
            final ExecContextSegmentParams p = s.getExecContextSegmentParams();
            p.lines.removeIf(l -> removed.containsKey(l.ctx));
            p.states.keySet().removeIf(taskIds::contains);
            p.triesWasMade.keySet().removeIf(taskIds::contains);
            p.variableStates.removeIf(e -> taskIds.contains(e.taskId));
            if (p.lines.isEmpty()) {
                segmentRepository.delete(s);
            }
            else {
                save(s, p);
            }
        }

        // join records
        delta.forEach((joinTaskId, d) -> {
            final ExecContextJoin j = joinRepository.findByExecContextIdAndJoinTaskId(execContextId, joinTaskId);
            if (j == null) {
                throw new IllegalStateException("01.913.160 join #" + joinTaskId + " of removed registered lines has no record, ExecContext #" + execContextId);
            }
            j.linesRegistered -= d[0];
            j.linesFinished -= d[1];
            j.linesDead -= d[2];
            joinRepository.save(j);
        });
        for (Long t : taskIds) {
            final ExecContextJoin j = joinRepository.findByExecContextIdAndJoinTaskId(execContextId, t);
            if (j != null) {
                joinRepository.delete(j);
            }
        }
    }

    private String ctxOf(Long taskId) {
        final TaskImpl t = taskRepository.findByIdReadOnly(taskId);
        if (t == null) {
            throw new IllegalStateException("01.913.090 Task #" + taskId + " not found");
        }
        return t.getTaskParamsYaml().task.taskContextId;
    }
}
