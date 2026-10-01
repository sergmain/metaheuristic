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
import java.util.function.Function;
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
        // 041 Phase 9: one implementation - the lookup form below; a LineIndex is one such lookup
        return derivedJoin(index::lineOf, line);
    }

    /**
     * {@link #derivedJoin(LineIndex, SegmentData.Line)} over a line lookup ({@code lineOf}: the line holding a Task), so
     * storage can load the fork's lines on demand instead of indexing every line of the ExecContext (Phase 9).
     */
    @Nullable
    public static Long derivedJoin(Function<Long, SegmentData.Line> lineOf, SegmentData.Line line) {
        SegmentData.Line current = line;
        while (true) {
            final Long fork = current.forkTaskId();
            if (fork == null) {
                return null;
            }
            final SegmentData.Line forkLine = lineOf.apply(fork);
            final int pos = positionIn(forkLine, fork);
            if (pos + 1 < forkLine.tasks().size()) {
                return forkLine.tasks().get(pos + 1).taskId();
            }
            current = forkLine;
        }
    }

    /** The index of {@code taskId} in {@code line}'s chain. */
    public static int positionIn(SegmentData.Line line, long taskId) {
        for (int i = 0; i < line.tasks().size(); i++) {
            if (line.tasks().get(i).taskId() == taskId) {
                return i;
            }
        }
        throw new IllegalStateException("01.906.130 Task #" + taskId + " is not in line " + line.ctx());
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
     * Every Task of the graph in a topological order (Kahn's algorithm): among the Tasks whose predecessors are all
     * placed, the smallest Task id goes first, so the order is deterministic (Phase 12: the segment counterpart of the
     * whole-graph {@code getAllTasksTopologically}). A cycle fails.
     */
    public static List<Long> topologicalOrder(SegmentData.Graph graph) {
        final Map<Long, Integer> inDegree = new HashMap<>();
        final Map<Long, List<Long>> out = new HashMap<>();
        for (Long id : graph.nodes().keySet()) {
            inDegree.put(id, 0);
        }
        for (SegmentData.Edge e : graph.edges()) {
            node(graph.nodes(), e.from());
            node(graph.nodes(), e.to());
            out.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
            inDegree.merge(e.to(), 1, Integer::sum);
        }
        final PriorityQueue<Long> ready = new PriorityQueue<>();
        inDegree.forEach((id, d) -> {
            if (d == 0) {
                ready.add(id);
            }
        });
        final List<Long> order = new ArrayList<>(inDegree.size());
        while (!ready.isEmpty()) {
            final Long id = ready.poll();
            order.add(id);
            for (Long to : out.getOrDefault(id, List.of())) {
                if (inDegree.merge(to, -1, Integer::sum) == 0) {
                    ready.add(to);
                }
            }
        }
        if (order.size() != inDegree.size()) {
            throw new IllegalStateException("01.906.180 the graph has a cycle: " + (inDegree.size() - order.size())
                    + " of " + inDegree.size() + " Tasks are on or behind it");
        }
        return order;
    }

    /**
     * Why a set of lines is not one valid line-based ExecContext structure, or null when it is (Phase 12: the segment
     * counterpart of the whole-graph {@code verifyGraph}). No lines is valid - today's check (fewer than two root
     * vertices) accepts an empty graph. Otherwise (a line always has a Task - {@link SegmentData.Line}): exactly one root
     * line; every fork is a Task of some line and the fork chain of every line reaches the root line; the implied graph
     * ({@link #toGraph}) decomposes
     * back into lines ({@link #decompose}: one chain per ctx, a head reached only from its fork, every tail wired to its
     * derived join) and is acyclic.
     */
    @Nullable
    public static String structureError(Collection<SegmentData.Line> lines) {
        if (lines.isEmpty()) {
            return null;
        }
        final LineIndex index;
        try {
            index = LineIndex.of(lines);
        }
        catch (IllegalStateException e) {
            return e.getMessage();
        }
        final long roots = lines.stream().filter(SegmentData.Line::isRoot).count();
        if (roots != 1) {
            return "01.906.150 exactly one root line is expected, found " + roots;
        }
        for (SegmentData.Line line : lines) {
            final Set<String> seen = new HashSet<>();
            seen.add(line.ctx());
            SegmentData.Line current = line;
            while (!current.isRoot()) {
                final long fork = Objects.requireNonNull(current.forkTaskId());
                final SegmentData.Line forkLine = index.lineOfTask().get(fork);
                if (forkLine == null) {
                    return "01.906.160 line " + current.ctx() + " forks from Task #" + fork + ", which is in no line";
                }
                if (!seen.add(forkLine.ctx())) {
                    return "01.906.170 the fork chain of line " + line.ctx() + " returns to line " + forkLine.ctx();
                }
                current = forkLine;
            }
        }
        try {
            final SegmentData.Graph graph = toGraph(lines);
            decompose(graph);
            topologicalOrder(graph);
        }
        catch (IllegalStateException e) {
            return e.getMessage();
        }
        return null;
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
