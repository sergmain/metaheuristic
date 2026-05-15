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

package ai.metaheuristic.ai.dispatcher.event.events;

import ai.metaheuristic.ai.dispatcher.vault.VaultEntryChangedEvent;
import lombok.AllArgsConstructor;

/**
 * Tx variant of {@link VaultEntryChangedEvent}: published from within the
 * @Transactional write path that updates Company.params. Converted by
 * {@code EventsBoundedToTx} to a plain {@link VaultEntryChangedEvent} after
 * the surrounding transaction commits.
 *
 * @author Sergio Lissner
 */
@AllArgsConstructor
public class VaultEntryChangedTxEvent {
    public final long companyId;
    public final String keyCode;
    public final String action;

    public VaultEntryChangedEvent to() {
        return new VaultEntryChangedEvent(companyId, keyCode, action);
    }
}
