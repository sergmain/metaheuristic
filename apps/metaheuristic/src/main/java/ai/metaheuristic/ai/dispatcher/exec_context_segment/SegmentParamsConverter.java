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

import ai.metaheuristic.ai.yaml.exec_context_segment.ExecContextSegmentParams;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Between the stored params of a segment record ({@link ExecContextSegmentParams}) and the algebra's immutable lines
 * (041-EXEC-CONTEXT-SEGMENTS-PLAN). Only the structure crosses: task states and variable-state entries stay in the params.
 *
 * <p>Error code prefix: {@code 01.912.} (unique to this class).
 */
public final class SegmentParamsConverter {

    private SegmentParamsConverter() {
    }

    /** The lines stored in {@code params}, in stored order. */
    public static List<SegmentData.Line> lines(ExecContextSegmentParams params) {
        final List<SegmentData.Line> lines = new ArrayList<>(params.lines.size());
        for (ExecContextSegmentParams.Line l : params.lines) {
            if (l.tasks.isEmpty()) {
                throw new IllegalStateException("01.912.020 stored line " + l.ctx + " has no Task");
            }
            lines.add(new SegmentData.Line(l.ctx, l.forkTaskId,
                    l.tasks.stream().map(v -> new SegmentData.Vertex(v.taskId, v.tag)).toList()));
        }
        return lines;
    }

    /** A segment of the algebra from a segment record's columns and params. */
    public static SegmentData.Segment segment(String lineCtxId, @Nullable Long forkTaskId, ExecContextSegmentParams params) {
        return new SegmentData.Segment(lineCtxId, forkTaskId, lines(params));
    }

    /** New params holding only the structure of {@code lines}; no task state, no variable-state entry. */
    public static ExecContextSegmentParams params(Collection<SegmentData.Line> lines) {
        final ExecContextSegmentParams p = new ExecContextSegmentParams();
        for (SegmentData.Line line : lines) {
            final ExecContextSegmentParams.Line l = new ExecContextSegmentParams.Line(line.ctx(), line.forkTaskId());
            for (SegmentData.Vertex v : line.tasks()) {
                l.tasks.add(new ExecContextSegmentParams.Vertex(v.taskId(), v.tag()));
            }
            p.lines.add(l);
        }
        return p;
    }
}
