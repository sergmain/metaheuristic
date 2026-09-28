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

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Task-state algebra over lines (041-EXEC-CONTEXT-SEGMENTS-PLAN, Phase 4, decision 11): which Tasks are ready, the
 * SKIPPED closure of a failure, and the descendants of a Task - all computed from the lines' structure, with no
 * whole-graph walk. Spring-less, storage-free.
 *
 * <p>A Task's parents are implied by the lines: its chain predecessor, or - for a line head - the line's fork; plus, for
 * a join, the tail of every line whose derived join it is. Its children, symmetrically.
 *
 * <p>Error code prefix: {@code 01.909.} (unique to this class).
 */
public final class SegmentStates {

    private SegmentStates() {
    }

    private static final String TAG_TERMINAL = "terminal";

    /** Parents and children of every Task, as implied by the lines. */
    public record Adjacency(SegmentAlgebra.LineIndex index, Map<Long, List<Long>> parents, Map<Long, List<Long>> children,
                            long rootHead, long leaf, Map<Long, SegmentData.Vertex> vertices) {

        public static Adjacency of(Collection<SegmentData.Line> lines) {
            final SegmentAlgebra.LineIndex index = SegmentAlgebra.LineIndex.of(lines);
            final Map<Long, List<Long>> parents = new HashMap<>();
            final Map<Long, List<Long>> children = new HashMap<>();
            final Map<Long, SegmentData.Vertex> vertices = new HashMap<>();
            SegmentData.Line root = null;
            for (SegmentData.Line line : lines) {
                SegmentData.Vertex prev = null;
                for (SegmentData.Vertex v : line.tasks()) {
                    vertices.put(v.taskId(), v);
                    parents.computeIfAbsent(v.taskId(), k -> new ArrayList<>());
                    children.computeIfAbsent(v.taskId(), k -> new ArrayList<>());
                    if (prev != null) {
                        link(parents, children, prev.taskId(), v.taskId());
                    }
                    prev = v;
                }
            }
            for (SegmentData.Line line : lines) {
                if (line.isRoot()) {
                    if (root != null) {
                        throw new IllegalStateException("01.909.010 more than one root line: " + root.ctx() + ", " + line.ctx());
                    }
                    root = line;
                    continue;
                }
                link(parents, children, Objects.requireNonNull(line.forkTaskId()), line.head().taskId());
                final Long join = SegmentAlgebra.derivedJoin(index, line);
                if (join == null) {
                    throw new IllegalStateException("01.909.020 line " + line.ctx() + " has no join");
                }
                link(parents, children, line.tail().taskId(), join);
            }
            if (root == null) {
                throw new IllegalStateException("01.909.030 no root line");
            }
            return new Adjacency(index, parents, children, root.head().taskId(), root.tail().taskId(), vertices);
        }

        private static void link(Map<Long, List<Long>> parents, Map<Long, List<Long>> children, long from, long to) {
            children.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
            parents.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
        }

        public List<Long> parentsOf(long taskId) {
            return parents.getOrDefault(taskId, List.of());
        }

        public List<Long> childrenOf(long taskId) {
            return children.getOrDefault(taskId, List.of());
        }

        public boolean isTerminal(long taskId) {
            final SegmentData.Vertex v = vertices.get(taskId);
            return v != null && TAG_TERMINAL.equals(v.tag());
        }
    }

    private static boolean finished(EnumsApi.TaskExecState s) {
        return EnumsApi.TaskExecState.isFinishedState(s);
    }

    static boolean dead(EnumsApi.TaskExecState s) {
        return s == EnumsApi.TaskExecState.ERROR || s == EnumsApi.TaskExecState.SKIPPED;
    }

    private static boolean pending(EnumsApi.TaskExecState s, boolean includeForCaching) {
        return s == EnumsApi.TaskExecState.NONE || (includeForCaching && s == EnumsApi.TaskExecState.CHECK_CACHE);
    }

    /**
     * The Tasks ready to be handed out. The unfinished root head is ready alone. Otherwise, in each line only the first
     * unfinished Task can be ready: it is when it is pending, all its parents are finished, and it is {@code tag terminal}
     * or one of its parents is live (not ERROR / SKIPPED). When nothing is ready that way, the leaf is ready once all its
     * parents are finished, however they finished.
     *
     * @param stateOf the exec state of a Task; a Task without one is NONE
     */
    public static Set<Long> ready(Adjacency adj, Collection<SegmentData.Line> lines,
                                  Function<Long, EnumsApi.TaskExecState> stateOf, boolean includeForCaching) {
        if (pending(stateOf.apply(adj.rootHead()), includeForCaching)) {
            return Set.of(adj.rootHead());
        }
        final Set<Long> ready = new TreeSet<>();
        for (SegmentData.Line line : lines) {
            final SegmentData.Vertex first = line.tasks().stream()
                    .filter(v -> !finished(stateOf.apply(v.taskId())))
                    .findFirst().orElse(null);
            if (first == null || !pending(stateOf.apply(first.taskId()), includeForCaching)) {
                continue;
            }
            final List<Long> parents = adj.parentsOf(first.taskId());
            if (!parents.stream().allMatch(p -> finished(stateOf.apply(p)))) {
                continue;
            }
            if (adj.isTerminal(first.taskId()) || parents.isEmpty() || parents.stream().anyMatch(p -> !dead(stateOf.apply(p)))) {
                ready.add(first.taskId());
            }
        }
        if (!ready.isEmpty()) {
            return ready;
        }
        final long leaf = adj.leaf();
        if (pending(stateOf.apply(leaf), includeForCaching)
                && adj.parentsOf(leaf).stream().allMatch(p -> finished(stateOf.apply(p)))) {
            return Set.of(leaf);
        }
        return Set.of();
    }

    /**
     * Applies the SKIPPED closure of a failed Task to {@code states}, in place, and returns the Tasks it marked. From the
     * seed's children onwards, a Task is marked SKIPPED when it is not already OK / ERROR / SKIPPED, is neither the leaf
     * nor {@code tag terminal}, and every parent is ERROR or SKIPPED; the walk continues from every Task it marks.
     *
     * @param states exec states by Task id; an absent Task is NONE
     */
    public static Set<Long> skipClosure(Adjacency adj, Map<Long, EnumsApi.TaskExecState> states, long seed) {
        // 041 Phase 9: one implementation - the function form below, here over the whole-ExecContext adjacency
        final Function<Long, EnumsApi.TaskExecState> stateOf = stateOf(states);
        return skipClosure(adj::childrenOf,
                c -> c == adj.leaf() || adj.isTerminal(c),
                c -> {
                    final List<Long> parents = adj.parentsOf(c);
                    return !parents.isEmpty() && parents.stream().allMatch(p -> dead(stateOf.apply(p)));
                },
                stateOf, c -> states.put(c, EnumsApi.TaskExecState.SKIPPED), seed);
    }

    /**
     * The SKIPPED closure over functions, so storage that loads lines on demand can run it (Phase 9). From the seed's
     * children onwards, a Task is marked when it is not already OK / ERROR / SKIPPED, is not {@code exempt} (the leaf,
     * {@code tag terminal}), and {@code allParentsDead} holds (false for a Task without parents); the walk continues from
     * every Task it marks.
     *
     * @param stateOf        the current state of a Task - it must see every earlier {@code markSkipped}
     * @param allParentsDead evaluated against the current states
     * @param markSkipped    records a Task as SKIPPED
     */
    public static Set<Long> skipClosure(Function<Long, List<Long>> childrenOf, Predicate<Long> exempt,
                                        Predicate<Long> allParentsDead, Function<Long, EnumsApi.TaskExecState> stateOf,
                                        Consumer<Long> markSkipped, long seed) {
        final Set<Long> marked = new TreeSet<>();
        final Deque<Long> queue = new ArrayDeque<>(childrenOf.apply(seed));
        while (!queue.isEmpty()) {
            final long c = queue.poll();
            final EnumsApi.TaskExecState s = stateOf.apply(c);
            if (s == EnumsApi.TaskExecState.OK || dead(s) || exempt.test(c)) {
                continue;
            }
            if (!allParentsDead.test(c)) {
                continue;
            }
            markSkipped.accept(c);
            marked.add(c);
            queue.addAll(childrenOf.apply(c));
        }
        return marked;
    }

    /** Every Task reachable from {@code taskId}, not including it. */
    public static Set<Long> descendants(Adjacency adj, long taskId) {
        final Set<Long> seen = new TreeSet<>();
        final Deque<Long> queue = new ArrayDeque<>(adj.childrenOf(taskId));
        while (!queue.isEmpty()) {
            final long c = queue.poll();
            if (seen.add(c)) {
                queue.addAll(adj.childrenOf(c));
            }
        }
        return seen;
    }

    /** Convenience: the state of a Task in a map, NONE when absent. */
    public static Function<Long, EnumsApi.TaskExecState> stateOf(Map<Long, EnumsApi.TaskExecState> states) {
        return id -> states.getOrDefault(id, EnumsApi.TaskExecState.NONE);
    }
}
