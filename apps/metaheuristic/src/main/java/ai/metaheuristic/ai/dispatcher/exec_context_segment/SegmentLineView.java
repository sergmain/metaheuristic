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
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * The lines of one ExecContext, loaded on demand (041-EXEC-CONTEXT-SEGMENTS-PLAN, Phases 9 and 10): a Task's line comes
 * from the segment owning its ctx, the lines a Task forks from its own segment and from the segments whose start line
 * forks from it. Nothing else of the ExecContext is read. One view serves one call; it caches what it has read.
 *
 * <p>Storage access is passed in as functions, so the view carries no Spring dependency.
 *
 * <p>Error code prefix: {@code 01.918.} (unique to this class).
 */
public final class SegmentLineView {

    private final Long execContextId;
    private final Function<String, @Nullable ExecContextSegment> segmentOfCtx;
    private final Function<Long, String> ctxOfTask;
    private final Function<Long, List<ExecContextSegment>> segmentsForkedFrom;

    private final Map<Long, String> ctxHint = new HashMap<>();
    private final Map<Long, ExecContextSegment> segmentOfTask = new HashMap<>();
    private final Map<Long, SegmentData.Line> lineOfTask = new HashMap<>();
    private final Map<Long, List<SegmentData.Line>> linesOfSegment = new HashMap<>();
    private final Map<String, ExecContextSegment> segmentOfLine = new HashMap<>();

    /**
     * @param segmentOfCtx       the segment owning a ctx (the nearest segment start up the ctx); null when there is none
     * @param ctxOfTask          the ctx of a Task, read from the Task
     * @param segmentsForkedFrom the segments whose start line forks from a Task
     */
    public SegmentLineView(Long execContextId, Function<String, @Nullable ExecContextSegment> segmentOfCtx,
                           Function<Long, String> ctxOfTask, Function<Long, List<ExecContextSegment>> segmentsForkedFrom) {
        this.execContextId = execContextId;
        this.segmentOfCtx = segmentOfCtx;
        this.ctxOfTask = ctxOfTask;
        this.segmentsForkedFrom = segmentsForkedFrom;
    }

    /** A known ctx of a Task, saving the read of the Task. */
    public void hint(Long taskId, String ctx) {
        ctxHint.put(taskId, ctx);
    }

    private void index(ExecContextSegment s) {
        if (linesOfSegment.containsKey(s.id)) {
            return;
        }
        final List<SegmentData.Line> lines = SegmentParamsConverter.lines(s.getExecContextSegmentParams());
        linesOfSegment.put(s.id, lines);
        for (SegmentData.Line l : lines) {
            segmentOfLine.put(l.ctx(), s);
            for (SegmentData.Vertex v : l.tasks()) {
                lineOfTask.put(v.taskId(), l);
                segmentOfTask.put(v.taskId(), s);
            }
        }
    }

    public SegmentData.Line lineOf(Long taskId) {
        final SegmentData.Line known = lineOfTask.get(taskId);
        if (known != null) {
            return known;
        }
        final String hint = ctxHint.get(taskId);
        final String ctx = hint != null ? hint : ctxOfTask.apply(taskId);
        final ExecContextSegment s = segmentOfCtx.apply(ctx);
        if (s == null) {
            throw new IllegalStateException("01.918.010 no segment owns ctx " + ctx + " of Task #" + taskId + ", ExecContext #" + execContextId);
        }
        index(s);
        final SegmentData.Line line = lineOfTask.get(taskId);
        if (line == null) {
            throw new IllegalStateException("01.918.020 Task #" + taskId + " (ctx " + ctx + ") is not in segment " + s.lineCtxId
                    + ", ExecContext #" + execContextId);
        }
        return line;
    }

    /** The segment holding a Task's line. */
    public ExecContextSegment segmentOf(Long taskId) {
        lineOf(taskId);
        return segmentOfTask.get(taskId);
    }

    /** The stored line with ctx {@code lineCtx} - its params, with the {@code registered} flag. The line must be loaded. */
    public ai.metaheuristic.ai.yaml.exec_context_segment.ExecContextSegmentParams.Line paramsLine(String lineCtx) {
        final ExecContextSegment s = segmentOfLine.get(lineCtx);
        if (s == null) {
            throw new IllegalStateException("01.918.030 line " + lineCtx + " is not loaded, ExecContext #" + execContextId);
        }
        for (ai.metaheuristic.ai.yaml.exec_context_segment.ExecContextSegmentParams.Line l : s.getExecContextSegmentParams().lines) {
            if (lineCtx.equals(l.ctx)) {
                return l;
            }
        }
        throw new IllegalStateException("01.918.040 segment " + s.lineCtxId + " has no line " + lineCtx + ", ExecContext #" + execContextId);
    }

    /** The segment holding the line with ctx {@code lineCtx}. The line must be loaded. */
    public ExecContextSegment segmentOfLine(String lineCtx) {
        final ExecContextSegment s = segmentOfLine.get(lineCtx);
        if (s == null) {
            throw new IllegalStateException("01.918.050 line " + lineCtx + " is not loaded, ExecContext #" + execContextId);
        }
        return s;
    }

    /** A Task's vertex in its line - its tag. */
    public SegmentData.Vertex vertexOf(Long taskId) {
        final SegmentData.Line line = lineOf(taskId);
        return line.tasks().get(SegmentAlgebra.positionIn(line, taskId));
    }

    /** Lines forked from a Task: in its own segment (static sub-blocks) and segments starting at such a line. */
    public List<SegmentData.Line> linesForkedFrom(Long taskId) {
        lineOf(taskId);
        final List<ExecContextSegment> where = new ArrayList<>();
        where.add(segmentOfTask.get(taskId));
        for (ExecContextSegment s : segmentsForkedFrom.apply(taskId)) {
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
}
