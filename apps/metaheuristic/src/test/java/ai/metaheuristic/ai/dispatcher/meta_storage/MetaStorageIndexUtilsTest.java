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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * MetaStorageIndexUtils has no Spring dependency, so the whole index rule set is exercisable
 * directly - the registry join, the description placeholder, and the entitlement difference
 * expressed as a lambda.
 *
 * <p>❗ companyId values are 2L, 7L and 42L. 1L is reserved for the MH management company.
 *
 * @author Serge
 */
@Execution(CONCURRENT)
public class MetaStorageIndexUtilsTest {

    private static MetaStorageData.TypeRef ref(long companyId, String type) {
        return new MetaStorageData.TypeRef(companyId, type);
    }

    /** Descriptions are addressed by (companyId, metaTable), exactly as the registry's key is. */
    private static Map<MetaStorageData.TypeRef, String> descriptions(Object... pairs) {
        final Map<MetaStorageData.TypeRef, String> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((MetaStorageData.TypeRef) pairs[i], (String) pairs[i + 1]);
        }
        return m;
    }

    // ---------- the description column ----------

    @Test
    public void test_descriptionOrPlaceholder_nullIsThePlaceholder() {
        // the common case: a table nobody registered a descriptor for
        assertEquals(MetaStorageIndexUtils.NO_DESCRIPTION, MetaStorageIndexUtils.descriptionOrPlaceholder(null));
    }

    @Test
    public void test_descriptionOrPlaceholder_emptyIsThePlaceholder() {
        assertEquals(MetaStorageIndexUtils.NO_DESCRIPTION, MetaStorageIndexUtils.descriptionOrPlaceholder(""));
    }

    @Test
    public void test_descriptionOrPlaceholder_whitespaceOnlyIsThePlaceholder() {
        // a descriptor registered with a blank desc documents nothing, so it reads as absent
        assertEquals(MetaStorageIndexUtils.NO_DESCRIPTION, MetaStorageIndexUtils.descriptionOrPlaceholder("   \t \n "));
    }

    @Test
    public void test_descriptionOrPlaceholder_realTextSurvives() {
        assertEquals("drone requirements, one record per reqId",
                MetaStorageIndexUtils.descriptionOrPlaceholder("drone requirements, one record per reqId"));
    }

    @Test
    public void test_descriptionOrPlaceholder_isTrimmed() {
        assertEquals("what this table is for",
                MetaStorageIndexUtils.descriptionOrPlaceholder("  what this table is for\n"));
    }

    // ---------- toTypeRefs: widening the single-company read ----------

    @Test
    public void test_toTypeRefs_stampsTheCompanyOnEveryName() {
        final List<MetaStorageData.TypeRef> refs =
                MetaStorageIndexUtils.toTypeRefs(7L, List.of("alpha", "beta", "gamma"));

        assertEquals(3, refs.size());
        assertTrue(refs.stream().allMatch(r -> r.companyId()==7L),
                "every ref carries the company the names were read under");
        assertEquals(List.of("alpha", "beta", "gamma"), refs.stream().map(MetaStorageData.TypeRef::type).toList(),
                "the query's ordering is preserved, not re-derived");
    }

    @Test
    public void test_toTypeRefs_emptyStoreYieldsEmptyList() {
        assertEquals(List.of(), MetaStorageIndexUtils.toTypeRefs(7L, List.of()));
    }

    // ---------- index: the join ----------

    @Test
    public void test_index_joinsDescriptionsByMetaTableName() {
        final Map<MetaStorageData.TypeRef, String> descriptions = descriptions(
                ref(7L, "drone-reqs"), "drone requirements",
                ref(7L, "audit-log"), "one record per audited act");

        final List<MetaStorageViewData.MetaTableItem> items = MetaStorageIndexUtils.index(
                List.of(ref(7L, "drone-reqs"), ref(7L, "audit-log")),
                descriptions::get,
                id -> null);

        assertEquals(2, items.size());
        assertEquals("drone requirements", items.get(0).description());
        assertEquals("one record per audited act", items.get(1).description());
    }

    @Test
    public void test_index_unregisteredTableGetsThePlaceholder() {
        // PHASE #1: one of the two tables has a descriptor, the other has none
        final List<MetaStorageViewData.MetaTableItem> items = MetaStorageIndexUtils.index(
                List.of(ref(7L, "described"), ref(7L, "undescribed")),
                descriptions(ref(7L, "described"), "it is described")::get,
                id -> null);

        // PHASE #2: the listing is driven by the store, not by the registry - a table with no
        // descriptor is still a table, and it must appear
        assertEquals(2, items.size(), "PHASE #2: an unregistered table is still listed");
        assertEquals("it is described", items.get(0).description());
        assertEquals(MetaStorageIndexUtils.NO_DESCRIPTION, items.get(1).description(), "PHASE #2: placeholder");
    }

    @Test
    public void test_index_preservesTheOrderOfTheRefs() {
        // the query sorts; re-sorting here would mean two owners of one ordering rule
        final List<MetaStorageViewData.MetaTableItem> items = MetaStorageIndexUtils.index(
                List.of(ref(42L, "zeta"), ref(2L, "alpha"), ref(7L, "mu")),
                t -> null,
                id -> null);

        assertEquals(List.of("zeta", "alpha", "mu"),
                items.stream().map(MetaStorageViewData.MetaTableItem::metaTable).toList());
    }

    @Test
    public void test_index_emptyStoreYieldsEmptyIndex() {
        assertEquals(List.of(), MetaStorageIndexUtils.index(List.of(), t -> "unused", id -> "unused"));
    }

    // ---------- index: the same name under two companies ----------

    @Test
    public void test_index_sameTableNameUnderTwoCompaniesStaysTwoEntries() {
        // PHASE #1: the store is partitioned by company, so this is two tables holding two
        // unrelated sets of records - collapsing them would report something that does not exist
        final List<MetaStorageViewData.MetaTableItem> items = MetaStorageIndexUtils.index(
                List.of(ref(2L, "drone-reqs"), ref(7L, "drone-reqs")),
                descriptions(
                        ref(2L, "drone-reqs"), "Acme's drone requirements",
                        ref(7L, "drone-reqs"), "Globex's drone requirements")::get,
                id -> "company-" + id);

        assertEquals(2, items.size(), "PHASE #1: two entries, one per partition");
        assertEquals(2L, items.get(0).companyId());
        assertEquals(7L, items.get(1).companyId());

        // PHASE #2: and each carries ITS OWN company's description - the registry is keyed by
        // (COMPANY_ID, META_TABLE, PROD), so a name alone never resolves to someone else's answer
        assertEquals("Acme's drone requirements", items.get(0).description());
        assertEquals("Globex's drone requirements", items.get(1).description());
    }

    @Test
    public void test_index_aDescriptorOfAnotherCompanysTableIsNotBorrowed() {
        // only company 2 registered 'drone-reqs'; company 7's table of the same name is undescribed
        final List<MetaStorageViewData.MetaTableItem> items = MetaStorageIndexUtils.index(
                List.of(ref(2L, "drone-reqs"), ref(7L, "drone-reqs")),
                descriptions(ref(2L, "drone-reqs"), "Acme's drone requirements")::get,
                id -> "company-" + id);

        assertEquals("Acme's drone requirements", items.get(0).description());
        assertEquals(MetaStorageIndexUtils.NO_DESCRIPTION, items.get(1).description(),
                "an unregistered table shows the placeholder rather than the neighbour's description");
    }

    // ---------- index: entitlement is the lambda ----------

    @Test
    public void test_index_companyNameIsResolvedForTheAcrossCompaniesCaller() {
        final Map<Long, String> names = Map.of(2L, "Acme", 7L, "Globex");

        final List<MetaStorageViewData.MetaTableItem> items = MetaStorageIndexUtils.index(
                List.of(ref(2L, "alpha"), ref(7L, "beta")),
                t -> null,
                names::get);

        assertEquals("Acme", items.get(0).companyName());
        assertEquals("Globex", items.get(1).companyName());
    }

    @Test
    public void test_index_companyNameIsNullForTheSingleCompanyCaller() {
        // an ADMIN passes id -> null, and that is the whole of the difference: no branch inside
        // index() re-decides who may see what
        final List<MetaStorageViewData.MetaTableItem> items = MetaStorageIndexUtils.index(
                List.of(ref(7L, "alpha"), ref(7L, "beta")),
                t -> null,
                id -> null);

        assertTrue(items.stream().allMatch(i -> i.companyName()==null),
                "no owner-company name reaches a caller scoped to one company");
        assertTrue(items.stream().allMatch(i -> i.companyId()==7L),
                "the caller's own company is still on the item - it is their own partition");
    }

    @Test
    public void test_index_unresolvableCompanyNameIsWhateverTheResolverSays() {
        // meta storage has no FK to MH_COMPANY, so records outlive the company row. index() does not
        // invent a rendering for that - the resolver owns it, and here it is asked for one.
        final List<MetaStorageViewData.MetaTableItem> items = MetaStorageIndexUtils.index(
                List.of(ref(42L, "orphan")),
                t -> null,
                id -> "#" + id);

        assertEquals("#42", items.get(0).companyName());
    }
}
