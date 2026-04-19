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

package ai.metaheuristic.ai.dispatcher.signal_bus.events;

import ai.metaheuristic.ai.dispatcher.signal_bus.ScopeRef;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 02 Step 5. `.to()` copies the field references, not a defensive copy
 * of info — info is conventionally owned by the event, and mutating it after
 * publish is already a bug.
 */
class BatchStateSignalTxEventTest {

    @Test
    void to_copiesAllFields() {
        var tx = new BatchStateSignalTxEvent(42L, 4, new ScopeRef(100L),
            Map.of("state", 4, "stateName", "Finished"));

        //act
        BatchStateSignalEvent plain = tx.to();

        assertThat(plain.batchId()).isEqualTo(tx.batchId());
        assertThat(plain.state()).isEqualTo(tx.state());
        assertThat(plain.scope()).isEqualTo(tx.scope());
        assertThat(plain.info()).isSameAs(tx.info());  // same reference, not a defensive copy
    }
}
