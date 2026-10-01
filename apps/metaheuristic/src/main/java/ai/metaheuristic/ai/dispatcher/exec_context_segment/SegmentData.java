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

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable data of the segment algebra (041-EXEC-CONTEXT-SEGMENTS-PLAN, Phase 4). Knows only Task ids,
 * {@code taskContextId}s and tags - no storage, no Spring.
 *
 * <p>An ExecContext graph is line-based: every Task belongs to exactly one {@link Line} - the chain of Tasks that share a
 * {@code taskContextId} - and the only edges are the ones a set of lines implies: within a chain, fork -> line head,
 * and line tail -> the line's derived join.
 *
 * <p>Error code prefix: {@code 01.908.} (unique to this class).
 */
public final class SegmentData {

    private SegmentData() {
    }

    /** One vertex of a graph as a DOT stores it. */
    public record Node(long taskId, String ctx, @Nullable String tag) {
    }

    public record Edge(long from, long to) {
    }

    /** A whole graph: its vertices by Task id, and its edges. */
    public record Graph(Map<Long, Node> nodes, Set<Edge> edges) {
    }

    /** One Task of a line. */
    public record Vertex(long taskId, @Nullable String tag) {
    }

    /**
     * One chain of Tasks at one {@code taskContextId}, in chain order. {@code forkTaskId} is the Task whose edge leads to
     * the line's head; null only for the root line (the ExecContext's top-level chain).
     */
    public record Line(String ctx, @Nullable Long forkTaskId, List<Vertex> tasks) {
        public Line {
            tasks = List.copyOf(tasks);
            if (tasks.isEmpty()) {
                throw new IllegalArgumentException("01.908.010 a line has at least one Task, ctx " + ctx);
            }
        }

        public Vertex head() {
            return tasks.getFirst();
        }

        public Vertex tail() {
            return tasks.getLast();
        }

        public boolean isRoot() {
            return forkTaskId == null;
        }
    }

    /**
     * One segment: the lines stored in one record. {@code lineCtxId} is the ctx of the line that starts the segment
     * (the root line for the root segment); {@code forkTaskId} is that line's fork.
     */
    public record Segment(String lineCtxId, @Nullable Long forkTaskId, List<Line> lines) {
        public Segment {
            lines = List.copyOf(lines);
        }
    }
}
