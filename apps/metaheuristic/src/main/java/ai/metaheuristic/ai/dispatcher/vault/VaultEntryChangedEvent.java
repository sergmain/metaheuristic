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

package ai.metaheuristic.ai.dispatcher.vault;

/**
 * A Vault entry was added, replaced, or deleted.
 *
 * <p>Plain (non-transactional) Spring event. Published by
 * {@code EventsBoundedToTx} as the AFTER_COMMIT conversion of
 * {@code VaultEntryChangedTxEvent}, which is published from inside the
 * {@code @Transactional} write path that updates Company.params. Listeners
 * such as {@code VaultInvalidationFanout} see this event only after the DB
 * write has committed, never before.
 *
 * <p>Not a signal-bus signal. The Signal Bus is for UI polling; this event
 * is for server-internal fan-out to enrolled Processors via
 * {@code VaultInvalidationFanout}.
 *
 * @param companyId  the company.uniqueId that owns the changed entry
 * @param keyCode    the Vault entry code
 * @param action     {@code "put"} for add/replace, {@code "delete"} for removal
 */
public record VaultEntryChangedEvent(long companyId, String keyCode, String action) {

    public static final String ACTION_PUT = "put";
    public static final String ACTION_DELETE = "delete";
}
