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

import ai.metaheuristic.commons.utils.ContextUtils;

import java.util.Collection;

/**
 * Lock-free allocation of a line's {@code taskContextId} (041-EXEC-CONTEXT-SEGMENTS-PLAN, decision 10). The seed is the
 * highest sibling index under the line base, read once per ExecContext; a new line takes index {@code seed + rowId}, where
 * {@code rowId} is its segment record's generated id. Row ids are unique and positive, so every allocated index is above
 * the seed - above every sibling that existed when the seed was read - and two allocations never meet; the
 * {@code UNIQUE(EXEC_CONTEXT_ID, LINE_CTX_ID)} index is the guarantee of last resort. Indices are not dense.
 *
 * <p>Error code prefix: {@code 01.910.} (unique to this class).
 */
public final class SegmentLineCtx {

    private SegmentLineCtx() {
    }

    /** The highest {@code #index} among {@code existingCtxIds} at level {@code base}; 0 when there is none. */
    public static long seed(String base, Collection<String> existingCtxIds) {
        long max = 0;
        for (String ctx : existingCtxIds) {
            if (!base.equals(ContextUtils.getLevel(ctx))) {
                continue;
            }
            final String path = ContextUtils.getPath(ctx);
            if (path == null) {
                continue;
            }
            max = Math.max(max, Long.parseLong(path));
        }
        return max;
    }

    /** The ctx of a new line at level {@code base}: {@code base#(seed + rowId)}. */
    public static String lineCtx(String base, long seed, long rowId) {
        if (rowId <= 0) {
            throw new IllegalArgumentException("01.910.020 a segment row id is positive, was " + rowId);
        }
        if (seed < 0) {
            throw new IllegalArgumentException("01.910.040 a seed is never negative, was " + seed);
        }
        return ContextUtils.buildTaskContextId(base, Long.toString(seed + rowId));
    }
}
