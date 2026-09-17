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

package ai.metaheuristic.ai.dispatcher.meta_storage;

import ai.metaheuristic.ai.dispatcher.data.MetaStorageViewData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The decision half of the meta storage index, kept free of Spring, JPA and the beans so the whole
 * rule set is exercisable as plain functions - the same shape as
 * {@code ai.metaheuristic.ai.dispatcher.account.AccountRoleEditUtils}.
 *
 * <p>Both collaborators arrive as functions rather than as maps or repositories, so the two
 * entitlements differ by the lambda passed in and by nothing else: a caller entitled to the whole
 * installation passes a real name resolver, a caller entitled to one company passes
 * {@code id -> null}. There is no branch here that could disagree with the branch that chose the
 * data, because there is only one branch and it is at the call site.
 *
 * <p>❗ Neither function is asked to decide anything. Resolution failures - a company that no longer
 * exists, a meta table with no descriptor - are expressed by returning null, and this class turns
 * null into whatever the UI should render. That keeps "what is missing" and "what missing looks
 * like" in one place.
 *
 * @author Serge
 */
public final class MetaStorageIndexUtils {

    /**
     * ❗ Rendered instead of an empty cell. A table with no descriptor and a table whose descriptor
     * says nothing are the same thing to a reader, and both need to be visibly distinct from a
     * description that failed to load.
     */
    public static final String NO_DESCRIPTION = "<No description were provided>";

    private MetaStorageIndexUtils() {
    }

    /**
     * What the description column shows for one meta table.
     *
     * <p>Blank counts as absent: a descriptor registered with an empty {@code desc} documents
     * nothing, and rendering an empty cell for it would look like a loading failure rather than
     * like a table nobody has described.
     */
    public static String descriptionOrPlaceholder(@Nullable String desc) {
        return desc==null || desc.isBlank() ? NO_DESCRIPTION : desc.strip();
    }

    /**
     * Join the tables a store holds to the descriptions the registry holds.
     *
     * <p>Order is the order of {@code refs} - the query already sorted, and re-sorting here would
     * mean this class owned an ordering rule the database also owns.
     *
     * @param refs the (companyId, metaTable) pairs the store actually holds. A table exists by
     *             virtue of records carrying its name, so this list is the authority for what the
     *             tab lists; the registry only annotates it.
     * @param descriptionByTable the registry lookup, keyed by the SAME pair. ❗ Not by name alone:
     *             a descriptor belongs to one company's table, so looking one up without the company
     *             would show company A's description against company B's table. Returns null for a
     *             table nobody registered, which is the common case rather than an error.
     * @param companyNameByCompanyId the owner-company lookup, or {@code id -> null} for a caller
     *             scoped to a single company. ❗ Returning null here is what leaves the column empty,
     *             so entitlement is expressed by the lambda and never re-derived inside this method.
     */
    public static List<MetaStorageViewData.MetaTableItem> index(
            List<MetaStorageData.TypeRef> refs,
            Function<MetaStorageData.TypeRef, @Nullable String> descriptionByTable,
            Function<Long, @Nullable String> companyNameByCompanyId) {

        final List<MetaStorageViewData.MetaTableItem> result = new ArrayList<>(refs.size());
        for (MetaStorageData.TypeRef ref : refs) {
            result.add(new MetaStorageViewData.MetaTableItem(
                    ref.companyId(),
                    companyNameByCompanyId.apply(ref.companyId()),
                    ref.type(),
                    descriptionOrPlaceholder(descriptionByTable.apply(ref))));
        }
        return result;
    }

    /**
     * The (companyId, metaTable) pairs for a caller scoped to ONE company.
     *
     * <p>The single-company read returns bare type names - the company is already known, so the
     * query has no reason to repeat it - and the index wants pairs. Widening here rather than at the
     * query keeps one shape flowing into {@link #index}, so the entitlement difference stays a
     * difference of DATA and never becomes a second code path through the join.
     */
    public static List<MetaStorageData.TypeRef> toTypeRefs(Long companyId, List<String> types) {
        final List<MetaStorageData.TypeRef> result = new ArrayList<>(types.size());
        for (String type : types) {
            result.add(new MetaStorageData.TypeRef(companyId, type));
        }
        return result;
    }
}
