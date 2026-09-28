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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 9 of 041-EXEC-CONTEXT-SEGMENTS-PLAN: how a state change moves join records ({@link SegmentStateChange}).
 *
 * <p>Layout: root line {@code 1 -> 2 -> 3} (3 is the leaf); line A {@code 10 -> 11} and line B {@code 20}, both forked
 * from 1, so both join 2. The join record of 2 is given per case - registered lines, finished, dead - or absent when no
 * line was registered (lines born SKIPPED by a PLACE_NOW graft).
 *
 * <p>Phase 11: the lines are registered exactly when the case gives a record; without one they are lines born SKIPPED,
 * and a revival (the tail leaving dead) registers them.
 */
@Execution(ExecutionMode.CONCURRENT)
public class SegmentJoinCountTest {

    private static final EnumsApi.TaskExecState NONE = EnumsApi.TaskExecState.NONE;
    private static final EnumsApi.TaskExecState OK = EnumsApi.TaskExecState.OK;
    private static final EnumsApi.TaskExecState ERROR = EnumsApi.TaskExecState.ERROR;
    private static final EnumsApi.TaskExecState SKIPPED = EnumsApi.TaskExecState.SKIPPED;
    private static final EnumsApi.TaskExecState IN_PROGRESS = EnumsApi.TaskExecState.IN_PROGRESS;

    private static SegmentData.Line line(String ctx, @Nullable Long fork, long... ids) {
        return new SegmentData.Line(ctx, fork, Arrays.stream(ids).mapToObj(id -> new SegmentData.Vertex(id, null)).toList());
    }

    private static List<SegmentData.Line> layout(@Nullable String tagOfJoin) {
        final SegmentData.Line root = new SegmentData.Line("1", null, List.of(
                new SegmentData.Vertex(1, null), new SegmentData.Vertex(2, tagOfJoin), new SegmentData.Vertex(3, null)));
        return List.of(root, line("1,2#1", 1L, 10, 11), line("1,2#2", 1L, 20));
    }

    private static SegmentStateChange.Lookup lookup(List<SegmentData.Line> lines, Map<Long, EnumsApi.TaskExecState> states,
                                                    Map<Long, SegmentStateChange.JoinCount> joins) {
        final SegmentAlgebra.LineIndex index = SegmentAlgebra.LineIndex.of(lines);
        final Map<Long, List<SegmentData.Line>> forked = new HashMap<>();
        lines.stream().filter(l -> !l.isRoot()).forEach(l -> forked.computeIfAbsent(l.forkTaskId(), k -> new ArrayList<>()).add(l));
        return new SegmentStateChange.Lookup(index::lineOf, t -> forked.getOrDefault(t, List.of()), joins::get,
                t -> states.getOrDefault(t, NONE), ctx -> !joins.isEmpty());
    }

    private static SegmentStateChange.Result apply(Map<Long, EnumsApi.TaskExecState> states, SegmentStateChange.@Nullable JoinCount joinOf2,
                                                   long taskId, EnumsApi.TaskExecState state) {
        return apply(layout(null), states, joinOf2, taskId, state);
    }

    private static SegmentStateChange.Result apply(List<SegmentData.Line> lines, Map<Long, EnumsApi.TaskExecState> states,
                                                   SegmentStateChange.@Nullable JoinCount joinOf2, long taskId, EnumsApi.TaskExecState state) {
        final Map<Long, SegmentStateChange.JoinCount> joins = joinOf2 == null ? Map.of() : Map.of(2L, joinOf2);
        return SegmentStateChange.apply(lookup(lines, states, joins), List.of(new SegmentStateChange.Change(taskId, state)));
    }

    private static SegmentStateChange.JoinCount jc(int registered, int finished, int dead) {
        return new SegmentStateChange.JoinCount(registered, finished, dead);
    }

    @Test
    public void test_tailOk_raisesFinished() {
        final SegmentStateChange.Result r = apply(Map.of(1L, OK, 10L, OK), jc(2, 0, 0), 11, OK);
        assertEquals(Map.of(11L, OK), r.states());
        assertEquals(Set.of(), r.skipped());
        assertEquals(Map.of(2L, jc(2, 1, 0)), r.joins(), "a tail finishing OK is one more finished line of its join");
    }

    @Test
    public void test_nonTailOk_leavesTheJoinAlone() {
        final SegmentStateChange.Result r = apply(Map.of(1L, OK), jc(2, 0, 0), 10, OK);
        assertEquals(Map.of(10L, OK), r.states());
        assertEquals(Map.of(), r.joins(), "only a line's tail moves its join");
    }

    @Test
    public void test_tailBackToPending_lowersFinished() {
        final SegmentStateChange.Result r = apply(Map.of(1L, OK, 10L, OK, 11L, OK), jc(2, 1, 0), 11, NONE);
        assertEquals(Map.of(2L, jc(2, 0, 0)), r.joins(), "a tail reopened is one finished line less");
    }

    @Test
    public void test_tailOkToError_movesFinishedToDead() {
        final SegmentStateChange.Result r = apply(Map.of(1L, OK, 20L, OK), jc(2, 1, 0), 20, ERROR);
        assertEquals(Map.of(2L, jc(2, 0, 1)), r.joins());
    }

    @Test
    public void test_errorInALine_killsTheRestOfTheChain_joinKeepsItsLiveFork() {
        final SegmentStateChange.Result r = apply(Map.of(1L, OK), jc(2, 0, 0), 10, ERROR);
        assertEquals(Set.of(11L), r.skipped(), "the rest of line A dies");
        assertEquals(Map.of(10L, ERROR, 11L, SKIPPED), r.states());
        assertEquals(Map.of(2L, jc(2, 0, 1)), r.joins(), "line A's tail is dead");
        assertFalse(r.states().containsKey(2L), "join 2 keeps a live parent - its fork 1 is OK");
    }

    @Test
    public void test_deadFork_killsItsLines_andTheJoinWhenEveryRegisteredLineIsDead() {
        final SegmentStateChange.Result r = apply(Map.of(), jc(2, 0, 0), 1, ERROR);
        assertEquals(Set.of(10L, 11L, 20L, 2L), r.skipped(), "both lines, then the join: its fork and both lines are dead");
        assertEquals(Map.of(2L, jc(2, 0, 2)), r.joins());
        assertFalse(r.states().containsKey(3L), "the leaf is never marked");
    }

    @Test
    public void test_deadFork_joinWithARegisteredLineStillLive_isNotMarked() {
        // three lines registered, only A and B are here: the third one's tail is live
        final SegmentStateChange.Result r = apply(Map.of(), jc(3, 0, 0), 1, ERROR);
        assertEquals(Set.of(10L, 11L, 20L), r.skipped(), "the join keeps a live parent - the third registered line");
        assertEquals(Map.of(2L, jc(3, 0, 2)), r.joins());
    }

    @Test
    public void test_deadFork_terminalJoin_isNeverMarked() {
        final SegmentStateChange.Result r = apply(layout("terminal"), Map.of(), jc(2, 0, 0), 1, ERROR);
        assertEquals(Set.of(10L, 11L, 20L), r.skipped(), "tag terminal: the join runs even when every parent is dead");
    }

    @Test
    public void test_unregisteredLines_leaveTheJoinAlone() {
        // no record: the lines were born SKIPPED by a PLACE_NOW graft and never registered
        final SegmentStateChange.Result ok = apply(Map.of(1L, OK, 10L, OK), null, 11, OK);
        assertEquals(Map.of(), ok.joins());
        final SegmentStateChange.Result err = apply(Map.of(1L, OK), null, 10, ERROR);
        assertEquals(Set.of(11L), err.skipped());
        assertEquals(Map.of(), err.joins());
    }

    @Test
    public void test_deadFork_noJoinRecord_joinDiesWithItsForkAlone() {
        // no line registered: the join's only parents that count are its chain predecessor - the fork
        final SegmentStateChange.Result r = apply(Map.of(), null, 1, ERROR);
        assertEquals(Set.of(10L, 11L, 20L, 2L), r.skipped());
    }

    @Test
    public void test_inProgressHead_underADeadFork_isMarked() {
        // as today's whole-graph closure: only OK / ERROR / SKIPPED Tasks are left as they are
        final SegmentStateChange.Result r = apply(Map.of(20L, IN_PROGRESS), jc(2, 0, 0), 1, ERROR);
        assertTrue(r.skipped().contains(20L));
    }

    @Test
    public void test_sameState_isANoOp() {
        final SegmentStateChange.Result r = apply(Map.of(1L, OK, 11L, OK), jc(2, 1, 0), 11, OK);
        assertEquals(Map.of(), r.states());
        assertEquals(Set.of(), r.skipped());
        assertEquals(Map.of(), r.joins());
    }

    @Test
    public void test_changesAppliedInOrder_secondSeesTheFirst() {
        final SegmentStateChange.Result r = SegmentStateChange.apply(lookup(layout(null), Map.of(1L, OK), Map.of(2L, jc(2, 0, 0))),
                List.of(new SegmentStateChange.Change(20, OK), new SegmentStateChange.Change(10, ERROR)));
        assertEquals(Map.of(2L, jc(2, 1, 1)), r.joins(), "B finished OK, A died");
        assertEquals(Set.of(11L), r.skipped());
    }

    @Test
    public void test_revivalOfAnUnregisteredLine_registersIt_andCreatesTheRecord() {
        // lines A and B born SKIPPED (PLACE_NOW), never registered: no record of join 2
        final Map<Long, EnumsApi.TaskExecState> states = Map.of(1L, OK, 10L, SKIPPED, 11L, SKIPPED, 20L, SKIPPED);
        final SegmentStateChange.Result r = SegmentStateChange.apply(lookup(layout(null), states, Map.of()),
                List.of(new SegmentStateChange.Change(10, EnumsApi.TaskExecState.INIT), new SegmentStateChange.Change(11, NONE)));
        assertEquals(Set.of("1,2#1"), r.registeredLines(), "the reset reopened A's tail: A registers itself");
        assertEquals(Map.of(2L, jc(1, 0, 0)), r.joins(), "join 2 gets a record with the one revived, pending line");
        assertEquals(Set.of(), r.skipped());
    }

    @Test
    public void test_revivedLine_thenFinishesOk_countsAsFinished() {
        final Map<Long, EnumsApi.TaskExecState> states = Map.of(1L, OK, 10L, SKIPPED, 11L, SKIPPED);
        final SegmentStateChange.Result r = SegmentStateChange.apply(lookup(layout(null), states, Map.of()),
                List.of(new SegmentStateChange.Change(11, NONE), new SegmentStateChange.Change(10, OK), new SegmentStateChange.Change(11, OK)));
        assertEquals(Map.of(2L, jc(1, 1, 0)), r.joins(), "registered by the revival, then one finished line");
    }

    @Test
    public void test_registeredLineWithoutRecord_isABrokenInvariant() {
        // registered (a record is given) - but for a different join than 2
        final Map<Long, SegmentStateChange.JoinCount> joins = Map.of(99L, jc(1, 0, 0));
        final IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> SegmentStateChange.apply(lookup(layout(null), Map.of(1L, OK, 10L, OK), joins), List.of(new SegmentStateChange.Change(11, OK))));
        assertTrue(e.getMessage().startsWith("01.914.060"), e.getMessage());
    }
}
