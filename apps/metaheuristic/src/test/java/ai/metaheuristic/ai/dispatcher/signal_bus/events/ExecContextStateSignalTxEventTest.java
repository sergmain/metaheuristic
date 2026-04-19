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

class ExecContextStateSignalTxEventTest {

    @Test
    void to_copiesAllFields() {
        var tx = new ExecContextStateSignalTxEvent(100L, 5, new ScopeRef(100L),
            Map.of("state", 5, "stateName", "FINISHED",
                   "infoBank", "DRONE", "sourceCodeUid", "mhdg-rg-flat-1.0.0"));

        //act
        ExecContextStateSignalEvent plain = tx.to();

        assertThat(plain.execContextId()).isEqualTo(tx.execContextId());
        assertThat(plain.state()).isEqualTo(tx.state());
        assertThat(plain.scope()).isEqualTo(tx.scope());
        assertThat(plain.info()).isSameAs(tx.info());
    }
}
