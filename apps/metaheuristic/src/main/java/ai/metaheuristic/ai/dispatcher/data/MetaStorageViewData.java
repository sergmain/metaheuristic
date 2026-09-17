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

    /**
     * Where one page sits in the whole result.
     *
     * <p>❗ Shaped as {@code {size, number, totalElements, totalPages}} to match what the UI's shared
     * pagination component reads, and built by hand rather than by serialising a Spring
     * {@code Page<T>}: Spring's page JSON has two shapes depending on a serialization-mode property,
     * and a screen should not break because an unrelated property was flipped.
     *
     * @param totalElements the REAL total from the count query, not the size of the page in hand.
     *                      Reporting the page's own size here makes totalPages 1 forever, which
     *                      silently disables Next.
     */
    public record PageInfo(int size, int number, long totalElements, int totalPages) {}

    /**
     * One page of the record keys of one meta table - ❗ keys only, never bodies.
     *
     * <p>A body is fetched one at a time, when the reader asks to see it. Sending every body with the
     * list would move an unbounded amount of data to render a screen that shows none of it, and the
     * bodies are MEDIUMTEXT.
     *
     * @param companyId the partition the listed table lives in, resolved server-side
     * @param metaTable echoed back, so a response overtaken by navigation can be recognised as stale
     * @param content this page's keys, named {@code content} because that is what the UI's pagination
     *                contract calls it
     */
    public record MetaTableRecordsResult(
            Long companyId, String metaTable, boolean production, List<String> content, PageInfo page) {}

    /**
     * One record's body, fetched on demand.
     *
     * @param found false when the key matched nothing - a record deleted between listing and opening
     *              is an ordinary outcome, not an error
     * @param body the stored string, verbatim. ❗ MH never parsed or validated a body, so this is
     *             whatever was written: it is supposed to be JSON and may not be. Deciding what it is
     *             belongs to the reader.
     */
    public record MetaTableRecordResult(
            Long companyId, String metaTable, boolean production, String recKey, boolean found, @Nullable String body) {}
}
