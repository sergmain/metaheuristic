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

import ai.metaheuristic.api.EnumsApi;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * Task exec-state changes on segmented storage (041-EXEC-CONTEXT-SEGMENTS-PLAN, Phase 9). Spring-less and storage-free:
 * the storage supplies what the change reads as functions ({@link Lookup}), and receives what changed ({@link Result}).
 *
 * <p>A change sets a Task's state. ERROR or SKIPPED also run the SKIPPED closure ({@link SegmentStates#skipClosure}) over
 * the parents and children the lines imply: a Task's chain predecessor, or - for a line head - the line's fork; and, for
 * a join, the tails of the lines registered with it, which are read from the join's record, not walked: every parent of
 * a join is dead when its chain predecessor is dead and every registered line is dead ({@code dead == registered}). A line
 * born SKIPPED (a PLACE_NOW graft) is never registered, and is dead anyway.
 *
 * <p>Whenever the tail of a non-root line moves between pending, OK and dead, the counts of its derived join move with it
 * - for a registered line; a join without a record has no registered line and is left alone.
 *
 * <p>Phase 11: a line knows whether it is registered ({@link Lookup#registered}). A line born SKIPPED is not; when its
 * tail leaves dead - a reset reviving it - it registers itself with its join (the record is created when absent) and is
 * reported in {@link Result#registeredLines()}, so storage marks it. An unregistered line moving any other way leaves the
 * join alone. A registered line whose join has no record is a broken invariant.
 *
 * <p>Error code prefix: {@code 01.914.} (unique to this class).
 */
public final class SegmentStateChange {

    private SegmentStateChange() {
    }

    private static final String TAG_TERMINAL = "terminal";

    /** The counts of one join record. */
    public record JoinCount(int registered, int finished, int dead) {
        JoinCount plus(int finishedDelta, int deadDelta) {
            return new JoinCount(registered, finished + finishedDelta, dead + deadDelta);
        }

        JoinCount register() {
            return new JoinCount(registered + 1, finished, dead);
        }
    }

    /**
     * Read access to the storage.
     *
     * @param lineOf          the line holding a Task
     * @param linesForkedFrom the lines whose fork is a Task
     * @param joinOf          the record of a join Task; null when no line is registered with it
     * @param stateOf         the stored state of a Task; NONE when it has none
     * @param registered      whether the line with a given ctx is counted in its join's record
     */
    public record Lookup(Function<Long, SegmentData.Line> lineOf,
                         Function<Long, List<SegmentData.Line>> linesForkedFrom,
                         Function<Long, @Nullable JoinCount> joinOf,
                         Function<Long, EnumsApi.TaskExecState> stateOf,
                         java.util.function.Predicate<String> registered) {}

    public record Change(long taskId, EnumsApi.TaskExecState state) {}

    /**
     * @param states  the new state of every Task that changed, requested or marked
     * @param skipped the Tasks the closure marked SKIPPED
     * @param joins   the new counts of every join record that moved
     * @param registeredLines the ctx of every line this change registered (Phase 11: a revived line)
     */
    public record Result(Map<Long, EnumsApi.TaskExecState> states, Set<Long> skipped, Map<Long, JoinCount> joins,
                         Set<String> registeredLines) {}

    /** Applies {@code changes} in order; a change to the state a Task already has is a no-op. */
    public static Result apply(Lookup lookup, List<Change> changes) {
        final Overlay o = new Overlay(lookup);
        final Set<Long> skipped = new TreeSet<>();
        for (Change c : changes) {
            if (o.state(c.taskId()) == c.state()) {
                continue;
            }
            o.set(c.taskId(), c.state());
            if (c.state() == EnumsApi.TaskExecState.ERROR || c.state() == EnumsApi.TaskExecState.SKIPPED) {
                skipped.addAll(SegmentStates.skipClosure(o::children, o::exempt, o::allParentsDead, o::state,
                        t -> o.set(t, EnumsApi.TaskExecState.SKIPPED), c.taskId()));
            }
        }
        return new Result(o.states, skipped, o.joins, o.registeredLines);
    }

    /** 0 pending, 1 OK, 2 dead - the join counts move when a tail changes category. */
    private static int category(EnumsApi.TaskExecState s) {
        return SegmentStates.dead(s) ? 2 : s == EnumsApi.TaskExecState.OK ? 1 : 0;
    }

    /** The storage's view with this change's writes on top. */
    private static final class Overlay {
        private final Lookup lookup;
        private final Map<Long, EnumsApi.TaskExecState> states = new TreeMap<>();
        private final Map<Long, JoinCount> joins = new TreeMap<>();
        private final Set<String> registeredLines = new TreeSet<>();

        private Overlay(Lookup lookup) {
            this.lookup = lookup;
        }

        EnumsApi.TaskExecState state(Long taskId) {
            final EnumsApi.TaskExecState s = states.get(taskId);
            return s != null ? s : lookup.stateOf().apply(taskId);
        }

        @Nullable
        JoinCount join(Long joinTaskId) {
            final JoinCount j = joins.get(joinTaskId);
            return j != null ? j : lookup.joinOf().apply(joinTaskId);
        }

        void set(Long taskId, EnumsApi.TaskExecState state) {
            final EnumsApi.TaskExecState old = state(taskId);
            states.put(taskId, state);
            final SegmentData.Line line = lookup.lineOf().apply(taskId);
            if (line.isRoot() || line.tail().taskId() != taskId) {
                return;
            }
            final int from = category(old);
            final int to = category(state);
            if (from == to) {
                return;
            }
            final Long joinTaskId = SegmentAlgebra.derivedJoin(lookup.lineOf(), line);
            if (joinTaskId == null) {
                throw new IllegalStateException("01.914.020 line " + line.ctx() + " has no join");
            }
            final boolean registered = registeredLines.contains(line.ctx()) || lookup.registered().test(line.ctx());
            if (!registered) {
                // born SKIPPED, never counted: only leaving dead - a revival - registers it
                if (from != 2) {
                    return;
                }
                final JoinCount current = join(joinTaskId);
                final JoinCount base = current != null ? current : new JoinCount(0, 0, 0);
                joins.put(joinTaskId, base.register().plus(to == 1 ? 1 : 0, 0));
                registeredLines.add(line.ctx());
                return;
            }
            final JoinCount j = join(joinTaskId);
            if (j == null) {
                // no line is registered with this join: the line was born SKIPPED (PLACE_NOW) and was never registered
                throw new IllegalStateException("01.914.060 line " + line.ctx() + " is registered, but its join #" + joinTaskId + " has no record");
            }
            joins.put(joinTaskId, j.plus((to == 1 ? 1 : 0) - (from == 1 ? 1 : 0), (to == 2 ? 1 : 0) - (from == 2 ? 1 : 0)));
        }

        /** Chain successor, heads of the lines it forks, and - for a non-root line's tail - the derived join. */
        List<Long> children(Long taskId) {
            // 041 Phase 10: one implementation, shared with the readers
            return SegmentStates.childrenOf(lookup.lineOf(), lookup.linesForkedFrom(), taskId);
        }

        /** The leaf (the root line's last Task) and {@code tag terminal} Tasks are never marked. */
        boolean exempt(Long taskId) {
            final SegmentData.Line line = lookup.lineOf().apply(taskId);
            if (line.isRoot() && line.tail().taskId() == taskId) {
                return true;
            }
            final int pos = SegmentAlgebra.positionIn(line, taskId);
            return TAG_TERMINAL.equals(line.tasks().get(pos).tag());
        }

        boolean allParentsDead(Long taskId) {
            final SegmentData.Line line = lookup.lineOf().apply(taskId);
            final int pos = SegmentAlgebra.positionIn(line, taskId);
            if (pos == 0) {
                // a line head: its one parent is the fork; the root head has no parent
                return !line.isRoot() && SegmentStates.dead(state(Objects.requireNonNull(line.forkTaskId())));
            }
            if (!SegmentStates.dead(state(line.tasks().get(pos - 1).taskId()))) {
                return false;
            }
            final JoinCount j = join(taskId);
            return j == null || j.dead() == j.registered();
        }
    }
}
