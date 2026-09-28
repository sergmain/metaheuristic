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
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextJoinRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextSegmentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.exec_context_segment.ExecContextSegmentParams;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Task exec state on segments (041-EXEC-CONTEXT-SEGMENTS-PLAN, Phase 9): the storage side of {@link SegmentStateChange}.
 * A state change reads and writes only the segments of the Tasks it touches - the changed Task's, and those the SKIPPED
 * closure reaches - and the join records of the lines whose tails moved; nothing else of the ExecContext is loaded.
 *
 * <p>Segments are loaded on demand: by the segment owning a Task's ctx, and - for the lines a Task forks - by
 * {@code FORK_TASK_ID}. A join record is read once per call. Only the rows that changed are written.
 *
 * <p>Error code prefix: {@code 01.916.} (unique to this class).
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ExecContextSegmentStateTxService {

    private final ExecContextSegmentRepository segmentRepository;
    private final ExecContextJoinRepository joinRepository;
    private final ExecContextSegmentTxService segmentTxService;
    private final TaskRepository taskRepository;

    /**
     * Sets the exec state of Tasks; ERROR or SKIPPED also marks the SKIPPED closure. Returns, as {@code childrenTasks},
     * the Tasks the closure marked - the caller persists them in {@code MH_TASK} (the same contract as the
     * whole-ExecContext {@code updateTaskExecState} this replaces).
     */
    @Transactional
    public ExecContextOperationStatusWithTaskList updateTaskExecStates(Long execContextId, List<TaskData.TaskWithStateAndTaskContextId> changes) {
        TxUtils.checkTxExists();
        final Loaded loaded = new Loaded(execContextId);
        changes.forEach(c -> loaded.ctxHint.put(c.taskId, c.taskContextId));
        final SegmentStateChange.Result r = SegmentStateChange.apply(loaded.lookup(),
                changes.stream().map(c -> new SegmentStateChange.Change(c.taskId, c.state)).toList());
        loaded.write(r.states(), Map.of(), r.joins());

        final ExecContextOperationStatusWithTaskList status = new ExecContextOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);
        r.skipped().forEach(t -> status.childrenTasks.add(new TaskData.TaskWithState(t, EnumsApi.TaskExecState.SKIPPED)));
        return status;
    }

    /** Recovery: a Task that failed with recovery goes back to {@code state} and its tries are recorded, in its segment. */
    @Transactional
    public void resetForRecovery(Long execContextId, Long taskId, EnumsApi.TaskExecState state, int triesWasMade) {
        TxUtils.checkTxExists();
        final Loaded loaded = new Loaded(execContextId);
        final SegmentStateChange.Result r = SegmentStateChange.apply(loaded.lookup(), List.of(new SegmentStateChange.Change(taskId, state)));
        if (!r.skipped().isEmpty()) {
            throw new IllegalStateException("01.916.030 a recovery reset to " + state + " marked Tasks SKIPPED: " + r.skipped()
                    + ", Task #" + taskId + ", ExecContext #" + execContextId);
        }
        loaded.write(r.states(), Map.of(taskId, triesWasMade), r.joins());
    }

    /** The tries recorded for a Task in its segment; null when none were recorded. */
    @Nullable
    @Transactional(readOnly = true)
    public Integer triesWasMade(Long execContextId, Long taskId) {
        final Loaded loaded = new Loaded(execContextId);
        loaded.lineOf(taskId);
        return loaded.segmentOfTask.get(taskId).getExecContextSegmentParams().triesWasMade.get(taskId);
    }

    /** The segments and join records one call has read, indexed by Task. */
    private final class Loaded {
        private final Long execContextId;
        private final Map<Long, String> ctxHint = new HashMap<>();
        private final Map<Long, ExecContextSegment> segmentOfTask = new HashMap<>();
        private final Map<Long, SegmentData.Line> lineOfTask = new HashMap<>();
        private final Map<Long, List<SegmentData.Line>> linesOfSegment = new HashMap<>();
        private final Map<Long, Optional<ExecContextJoin>> joins = new HashMap<>();

        private Loaded(Long execContextId) {
            this.execContextId = execContextId;
        }

        SegmentStateChange.Lookup lookup() {
            return new SegmentStateChange.Lookup(this::lineOf, this::linesForkedFrom, this::joinOf, this::stateOf);
        }

        private void index(ExecContextSegment s) {
            if (linesOfSegment.containsKey(s.id)) {
                return;
            }
            final List<SegmentData.Line> lines = SegmentParamsConverter.lines(s.getExecContextSegmentParams());
            linesOfSegment.put(s.id, lines);
            for (SegmentData.Line l : lines) {
                for (SegmentData.Vertex v : l.tasks()) {
                    lineOfTask.put(v.taskId(), l);
                    segmentOfTask.put(v.taskId(), s);
                }
            }
        }

        SegmentData.Line lineOf(Long taskId) {
            final SegmentData.Line known = lineOfTask.get(taskId);
            if (known != null) {
                return known;
            }
            final String hint = ctxHint.get(taskId);
            final String ctx = hint != null ? hint : ctxOf(taskId);
            final ExecContextSegment s = segmentTxService.findSegmentOfCtx(execContextId, ctx);
            if (s == null) {
                throw new IllegalStateException("01.916.010 no segment owns ctx " + ctx + " of Task #" + taskId + ", ExecContext #" + execContextId);
            }
            index(s);
            final SegmentData.Line line = lineOfTask.get(taskId);
            if (line == null) {
                throw new IllegalStateException("01.916.020 Task #" + taskId + " (ctx " + ctx + ") is not in segment " + s.lineCtxId
                        + ", ExecContext #" + execContextId);
            }
            return line;
        }

        /** Lines forked from {@code taskId}: in its own segment (static sub-blocks) and segments starting at such a line. */
        List<SegmentData.Line> linesForkedFrom(Long taskId) {
            lineOf(taskId);
            final List<ExecContextSegment> where = new ArrayList<>();
            where.add(segmentOfTask.get(taskId));
            for (ExecContextSegment s : segmentRepository.findByExecContextIdAndForkTaskId(execContextId, taskId)) {
                index(s);
                where.add(s);
            }
            final Set<String> seen = new HashSet<>();
            final List<SegmentData.Line> out = new ArrayList<>();
            for (ExecContextSegment s : where) {
                for (SegmentData.Line l : linesOfSegment.get(s.id)) {
                    if (taskId.equals(l.forkTaskId()) && seen.add(l.ctx())) {
                        out.add(l);
                    }
                }
            }
            return out;
        }

        SegmentStateChange.@Nullable JoinCount joinOf(Long joinTaskId) {
            return joins.computeIfAbsent(joinTaskId,
                            k -> Optional.ofNullable(joinRepository.findByExecContextIdAndJoinTaskId(execContextId, k)))
                    .map(j -> new SegmentStateChange.JoinCount(j.linesRegistered, j.linesFinished, j.linesDead))
                    .orElse(null);
        }

        EnumsApi.TaskExecState stateOf(Long taskId) {
            lineOf(taskId);
            return segmentOfTask.get(taskId).getExecContextSegmentParams().states.getOrDefault(taskId, EnumsApi.TaskExecState.NONE);
        }

        /** Writes the changed states and tries into their segments, and the moved counts into their join records. */
        void write(Map<Long, EnumsApi.TaskExecState> states, Map<Long, Integer> tries, Map<Long, SegmentStateChange.JoinCount> joinCounts) {
            final Map<Long, ExecContextSegment> dirty = new LinkedHashMap<>();
            states.forEach((taskId, state) -> {
                final ExecContextSegment s = segmentOf(taskId);
                s.getExecContextSegmentParams().states.put(taskId, state);
                dirty.put(s.id, s);
            });
            tries.forEach((taskId, n) -> {
                final ExecContextSegment s = segmentOf(taskId);
                s.getExecContextSegmentParams().triesWasMade.put(taskId, n);
                dirty.put(s.id, s);
            });
            // a state or tries change leaves the structure - and so STRUCTURE_HASH - as it is (decision 12)
            for (ExecContextSegment s : dirty.values()) {
                final ExecContextSegmentParams p = s.getExecContextSegmentParams();
                s.updateParams(p);
                segmentRepository.save(s);
            }
            joinCounts.forEach((joinTaskId, c) -> {
                final ExecContextJoin j = joins.getOrDefault(joinTaskId, Optional.empty()).orElseThrow(
                        () -> new IllegalStateException("01.916.040 join #" + joinTaskId + " moved but has no record, ExecContext #" + execContextId));
                j.linesFinished = c.finished();
                j.linesDead = c.dead();
                joinRepository.save(j);
            });
        }

        private ExecContextSegment segmentOf(Long taskId) {
            lineOf(taskId);
            return segmentOfTask.get(taskId);
        }

        private String ctxOf(Long taskId) {
            final TaskImpl t = taskRepository.findByIdReadOnly(taskId);
            if (t == null) {
                throw new IllegalStateException("01.916.050 Task #" + taskId + " not found, ExecContext #" + execContextId);
            }
            return t.getTaskParamsYaml().task.taskContextId;
        }
    }
}
