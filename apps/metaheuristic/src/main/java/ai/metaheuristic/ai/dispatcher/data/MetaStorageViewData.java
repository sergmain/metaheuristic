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

package ai.metaheuristic.ai.dispatcher.data;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What the meta storage index screen is sent.
 *
 * <p>Flat scalars rather than the beans, for the reason {@link ExecutionGateViewData} gives: a view
 * model sharing types with persistence drags the persistence shape into the UI and every later
 * change to one becomes a change to both.
 *
 * @author Serge
 */
public class MetaStorageViewData {

    /**
     * One meta table as the index lists it.
     *
     * <p>{@code companyId} is the partition the records live in, which is what makes two same-named
     * tables two different tables. {@code companyName} is resolved only when the caller is entitled
     * to see across companies - see {@link MetaTablesResult#showCompany()} - and is null otherwise.
     *
     * <p>{@code description} is never null: a table with no descriptor in MH_META_STORAGE_REGISTRY
     * carries the placeholder instead, so the UI renders one shape rather than branching on absence.
     */
    public record MetaTableItem(Long companyId, @Nullable String companyName, String metaTable, String description) {}

    /**
     * One tab's worth of the index.
     *
     * @param production true for MH_META_STORAGE, false for MH_META_STORAGE_SYNTHETIC. Echoed back
     *                   so a response that overtakes a tab switch can be recognised as stale.
     * @param showCompany whether the caller was entitled to the whole installation. ❗ Decided
     *                    server-side from the authenticated principal, never from a request
     *                    parameter, and it is what tells the UI to render the owner-company column.
     */
    public record MetaTablesResult(boolean production, boolean showCompany, List<MetaTableItem> tables) {}
}
