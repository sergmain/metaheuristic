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

import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

/**
 * Pure structural algebra of ExecContext segments (041-EXEC-CONTEXT-SEGMENTS-PLAN, Phase 4): a graph <-> its lines, the
 * derived join of a line, and the grouping of lines into segments. Spring-less, storage-free, no shared state.
 *
 * <p>The graph is line-based. A line is the chain of Tasks sharing one {@code taskContextId}. Its head is reached by
 * exactly one edge, from its fork; its tail has exactly one edge out of the line, to the line's <b>derived join</b>:
 * the Task after the fork in the fork's own chain, or - when the fork is the last Task of its chain - the derived join
 * of the fork's line, recursively. The root line (the top-level chain) has no fork and no join. Those three edge
 * kinds (within a chain, fork -> head, tail -> derived join) are the only ones allowed; anything else is rejected.
 *
 * <p>Error code prefix: {@code 01.906.} (unique to this class).
 */
public final class SegmentAlgebra {

    private SegmentAlgebra() {
    }

    /** Lookup over a set of lines: line by ctx, the line of each Task, each Task's position in its line. */
    public record LineIndex(Map<String, SegmentData.Line> byCtx, Map<Long, SegmentData.Line> lineOfTask,
                            Map<Long, Integer> position) {

        public static LineIndex of(Collection<SegmentData.Line> lines) {
            final Map<String, SegmentData.Line> byCtx = new HashMap<>();
            final Map<Long, SegmentData.Line> lineOfTask = new HashMap<>();
            final Map<Long, Integer> position = new HashMap<>();
            for (SegmentData.Line line : lines) {
                if (byCtx.put(line.ctx(), line) != null) {
                    throw new IllegalStateException("01.906.015 two lines share ctx " + line.ctx());
                }
                for (int i = 0; i < line.tasks().size(); i++) {
                    long taskId = line.tasks().get(i).taskId();
                    if (lineOfTask.put(taskId, line) != null) {
                        throw new IllegalStateException("01.906.017 Task #" + taskId + " belongs to more than one line");
                    }
                    position.put(taskId, i);
                }
            }
            return new LineIndex(byCtx, lineOfTask, position);
        }

        public SegmentData.Line lineOf(long taskId) {
            final SegmentData.Line line = lineOfTask.get(taskId);
            if (line == null) {
                throw new IllegalStateException("01.906.019 Task #" + taskId + " is in no line");
            }
            return line;
        }

        /** The Task after {@code taskId} in its own chain, or null when it is the chain's last Task. */
        @Nullable
        public Long nextInChain(long taskId) {
            final SegmentData.Line line = lineOf(taskId);
            final int pos = Objects.requireNonNull(position.get(taskId));
            return pos + 1 < line.tasks().size() ? line.tasks().get(pos + 1).taskId() : null;
        }
    }

    private static final Comparator<SegmentData.Line> LINE_ORDER =
            Comparator.comparing((SegmentData.Line l) -> !l.isRoot()).thenComparing(SegmentData.Line::ctx);

    /**
     * The derived join of {@code line}: the Task after its fork in the fork's chain, or, when the fork is the last Task of
     * its chain, the derived join of the fork's line. Null for the root line, and for a line whose fork chain ends in the
     * root line's last Task.
     */
    @Nullable
    public static Long derivedJoin(LineIndex index, SegmentData.Line line) {
        SegmentData.Line current = line;
        while (true) {
            final Long fork = current.forkTaskId();
            if (fork == null) {
                return null;
            }
            final Long next = index.nextInChain(fork);
            if (next != null) {
                return next;
            }
            current = index.lineOf(fork);
        }
    }

    /** The derived join of every non-root line, by line ctx. */
    public static Map<String, Long> derivedJoins(Collection<SegmentData.Line> lines) {
        final LineIndex index = LineIndex.of(lines);
        final Map<String, Long> joins = new TreeMap<>();
        for (SegmentData.Line line : lines) {
            if (line.isRoot()) {
                continue;
            }
            final Long join = derivedJoin(index, line);
            if (join == null) {
                throw new IllegalStateException("01.906.100 line " + line.ctx() + " has no join");
            }
            joins.put(line.ctx(), join);
        }
        return joins;
    }

    /**
     * Decomposes a graph into its lines, and rejects any graph that is not line-based. Lines come back root first, then
     * by ctx.
     */
    public static List<SegmentData.Line> decompose(SegmentData.Graph graph) {
        final Map<Long, SegmentData.Node> nodes = graph.nodes();
        final Map<Long, Long> chainNext = new HashMap<>();
        final Map<Long, Long> chainPrev = new HashMap<>();
        final Map<Long, List<Long>> incoming = new HashMap<>();
        for (SegmentData.Edge e : graph.edges()) {
            final SegmentData.Node from = node(nodes, e.from());
            final SegmentData.Node to = node(nodes, e.to());
            incoming.computeIfAbsent(e.to(), k -> new ArrayList<>()).add(e.from());
            if (from.ctx().equals(to.ctx())) {
                if (chainNext.put(e.from(), e.to()) != null) {
                    throw new IllegalStateException("01.906.020 Task #" + e.from() + " has two successors in its own ctx " + from.ctx());
                }
                if (chainPrev.put(e.to(), e.from()) != null) {
                    throw new IllegalStateException("01.906.020 Task #" + e.to() + " has two predecessors in its own ctx " + to.ctx());
                }
            }
        }

        final Map<String, List<SegmentData.Node>> byCtx = new HashMap<>();
        for (SegmentData.Node n : nodes.values()) {
            byCtx.computeIfAbsent(n.ctx(), k -> new ArrayList<>()).add(n);
        }

        final List<SegmentData.Line> lines = new ArrayList<>();
        int roots = 0;
        for (Map.Entry<String, List<SegmentData.Node>> en : byCtx.entrySet()) {
            final String ctx = en.getKey();
            final List<SegmentData.Node> heads = en.getValue().stream().filter(n -> !chainPrev.containsKey(n.taskId())).toList();
            if (heads.size() != 1) {
                throw new IllegalStateException("01.906.030 ctx " + ctx + " is not one chain: " + heads.size() + " heads");
            }
            final List<SegmentData.Vertex> tasks = new ArrayList<>();
            Long cur = heads.getFirst().taskId();
            while (cur != null) {
                final SegmentData.Node n = node(nodes, cur);
                tasks.add(new SegmentData.Vertex(n.taskId(), n.tag()));
                cur = chainNext.get(cur);
            }
            if (tasks.size() != en.getValue().size()) {
                throw new IllegalStateException("01.906.030 ctx " + ctx + " is not one chain: " + tasks.size() + " of "
                        + en.getValue().size() + " Tasks reachable from its head");
            }
            final long head = tasks.getFirst().taskId();
            final List<Long> into = incoming.getOrDefault(head, List.of());
            final Long fork;
            if (into.isEmpty()) {
                roots++;
                fork = null;
            }
            else if (into.size() == 1) {
                fork = into.getFirst();
            }
            else {
                throw new IllegalStateException("01.906.040 head #" + head + " of line " + ctx + " has " + into.size()
                        + " incoming edges " + into + ", a line head is reached only from its fork");
            }
            lines.add(new SegmentData.Line(ctx, fork, tasks));
        }
        if (roots != 1) {
            throw new IllegalStateException("01.906.050 a graph has exactly one root line, found " + roots);
        }

        final LineIndex index = LineIndex.of(lines);
        final Set<String> wiredToJoin = new HashSet<>();
        for (SegmentData.Edge e : graph.edges()) {
            final SegmentData.Line fromLine = index.lineOf(e.from());
            final SegmentData.Line toLine = index.lineOf(e.to());
            if (fromLine == toLine) {
                continue;
            }
            if (toLine.head().taskId() == e.to()) {
                // fork -> head: the head's only incoming edge, already checked
                continue;
            }
            if (fromLine.tail().taskId() != e.from()) {
                throw new IllegalStateException("01.906.060 edge #" + e.from() + " -> #" + e.to() + " leaves line "
                        + fromLine.ctx() + " from a Task that is not its tail");
            }
            final Long join = derivedJoin(index, fromLine);
            if (join == null) {
                throw new IllegalStateException("01.906.070 line " + fromLine.ctx() + " has no join but its tail #"
                        + e.from() + " has an edge to #" + e.to());
            }
            if (join != e.to()) {
                throw new IllegalStateException("01.906.080 tail #" + e.from() + " of line " + fromLine.ctx()
                        + " is wired to #" + e.to() + ", not to its derived join #" + join);
            }
            wiredToJoin.add(fromLine.ctx());
        }
        for (SegmentData.Line line : lines) {
            if (!line.isRoot() && !wiredToJoin.contains(line.ctx())) {
                throw new IllegalStateException("01.906.120 tail #" + line.tail().taskId() + " of line " + line.ctx()
                        + " is not wired to its derived join #" + derivedJoin(index, line));
            }
        }
        lines.sort(LINE_ORDER);
        return lines;
    }

    /** The graph a set of lines implies: every Task, chain edges, fork -> head, tail -> derived join. */
    public static SegmentData.Graph toGraph(Collection<SegmentData.Line> lines) {
        final LineIndex index = LineIndex.of(lines);
        final Map<Long, SegmentData.Node> nodes = new LinkedHashMap<>();
        final Set<SegmentData.Edge> edges = new LinkedHashSet<>();
        for (SegmentData.Line line : lines) {
            SegmentData.Vertex prev = null;
            for (SegmentData.Vertex v : line.tasks()) {
                nodes.put(v.taskId(), new SegmentData.Node(v.taskId(), line.ctx(), v.tag()));
                if (prev != null) {
                    edges.add(new SegmentData.Edge(prev.taskId(), v.taskId()));
                }
                prev = v;
            }
            if (line.isRoot()) {
                continue;
            }
            edges.add(new SegmentData.Edge(Objects.requireNonNull(line.forkTaskId()), line.head().taskId()));
            final Long join = derivedJoin(index, line);
            if (join == null) {
                throw new IllegalStateException("01.906.100 line " + line.ctx() + " has no join");
            }
            edges.add(new SegmentData.Edge(line.tail().taskId(), join));
        }
        return new SegmentData.Graph(nodes, edges);
    }

    /**
     * Groups lines into segments. The root line starts the root segment; a line starts its own segment when
     * {@code startsSegment} says so; every other line belongs to the segment of its fork's line. Segments come back root
     * first, then by line ctx; each segment's lines in the same order.
     */
    public static List<SegmentData.Segment> segments(Collection<SegmentData.Line> lines, Predicate<SegmentData.Line> startsSegment) {
        final LineIndex index = LineIndex.of(lines);
        final Map<String, SegmentData.Line> ownerByCtx = new HashMap<>();
        final Map<String, List<SegmentData.Line>> members = new HashMap<>();
        for (SegmentData.Line line : lines) {
            final SegmentData.Line owner = owner(index, line, startsSegment, ownerByCtx);
            members.computeIfAbsent(owner.ctx(), k -> new ArrayList<>()).add(line);
        }
        final List<SegmentData.Segment> segments = new ArrayList<>();
        members.entrySet().stream()
                .map(en -> Objects.requireNonNull(index.byCtx().get(en.getKey())))
                .sorted(LINE_ORDER)
                .forEach(owner -> {
                    final List<SegmentData.Line> ls = new ArrayList<>(members.get(owner.ctx()));
                    ls.sort(LINE_ORDER);
                    segments.add(new SegmentData.Segment(owner.ctx(), owner.forkTaskId(), ls));
                });
        return segments;
    }

    private static SegmentData.Line owner(LineIndex index, SegmentData.Line line, Predicate<SegmentData.Line> startsSegment,
                                          Map<String, SegmentData.Line> ownerByCtx) {
        final List<SegmentData.Line> path = new ArrayList<>();
        SegmentData.Line current = line;
        SegmentData.Line owner;
        while (true) {
            final SegmentData.Line known = ownerByCtx.get(current.ctx());
            if (known != null) {
                owner = known;
                break;
            }
            path.add(current);
            if (current.isRoot() || startsSegment.test(current)) {
                owner = current;
                break;
            }
            current = index.lineOf(Objects.requireNonNull(current.forkTaskId()));
        }
        for (SegmentData.Line l : path) {
            ownerByCtx.put(l.ctx(), owner);
        }
        return owner;
    }

    private static SegmentData.Node node(Map<Long, SegmentData.Node> nodes, long taskId) {
        final SegmentData.Node n = nodes.get(taskId);
        if (n == null) {
            throw new IllegalStateException("01.906.090 edge refers to Task #" + taskId + " which is not a vertex");
        }
        return n;
    }
}
