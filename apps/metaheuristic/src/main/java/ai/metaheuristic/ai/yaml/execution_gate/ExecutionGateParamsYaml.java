/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml.execution_gate;

import ai.metaheuristic.api.data.BaseParams;
import lombok.Data;
import org.jspecify.annotations.Nullable;

/**
 * <b>!!! BEFORE MAKING ANY EDITION IN THIS CLASS, READ <a href="https://github.com/sergmain/metaheuristic/wiki/multi-versioning-mechanic">...</a></b>
 * <br/>
 *
 * <p>The detail behind one durable admission block. The columns carry what has to be queried —
 * scope, key, deadline, reason — and this document carries what is only ever read back by a human
 * or by a diagnostic view, so it stays out of the index.
 *
 * <p>Everything here is nullable on purpose: a block opened by an operator has no failing Task
 * behind it, and a block opened from console analysis of a Task that never reached a Processor has
 * no processor id.
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@Data
public class ExecutionGateParamsYaml implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    /** The Task whose failure opened this block, when one did. */
    @Nullable public Long triggeredByTaskId;

    /** Code of the Function that ran when the block was opened. */
    @Nullable public String functionCode;

    /** The Processor that ran that Task. Null when the block was not opened from an execution. */
    @Nullable public Long processorId;

    /** Which declared pattern matched, kept verbatim so a wrong pattern can be identified later. */
    @Nullable public String matchedPattern;

    /**
     * A truncated excerpt of the output that matched. Truncated deliberately: console output is
     * unbounded and this row is read on the hot path's write side.
     */
    @Nullable public String consoleExcerpt;

    /** Whether the failing Task's retry counter was advanced when this block was opened. */
    public boolean incrementTries;
}
