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

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.MhSharedItTest;
import ai.metaheuristic.ai.SharedItEnv;
import ai.metaheuristic.ai.dispatcher.beans.MetaStorage;
import ai.metaheuristic.ai.dispatcher.beans.MetaStorageRegistry;
import ai.metaheuristic.ai.dispatcher.beans.MetaStorageSynthetic;
import ai.metaheuristic.ai.dispatcher.repositories.MetaStorageRegistryRepository;
import ai.metaheuristic.ai.dispatcher.repositories.MetaStorageRepository;
import ai.metaheuristic.ai.dispatcher.repositories.MetaStorageSyntheticRepository;
import ai.metaheuristic.api.data.meta_storage.MetaStorageRegistryParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cloning a meta table on the V3 harness - real Spring context, real H2, real rows in
 * MH_META_STORAGE, MH_META_STORAGE_SYNTHETIC and MH_META_STORAGE_REGISTRY.
 *
 * <p>No doubles, per RULE-NO-MOCKITO.md. What is under test is which physical table records land in,
 * whether the UNIQUE natural key and the @TableGenerator ids behave for inserts with no resolved id,
 * and whether a descriptor follows its table into the other store - all of which a stubbed repository
 * would simply agree with.
 *
 * <p>❗ {@code companyId} comes from {@link SharedItEnv#uniqueLong()} - never {@code 1L}, which is
 * reserved for the MH management company - and every table name from {@link SharedItEnv#uniqueCode}.
 * Isolation on the shared DB is by unique identifiers, not by teardown.
 *
 * @author Serge
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
public class MetaStorageCloneServiceTest extends MhSharedItTest {

    @Autowired private MetaStorageCloneService metaStorageCloneService;
    @Autowired private MetaStorageService metaStorageService;
    @Autowired private MetaStorageSyntheticService metaStorageSyntheticService;
    @Autowired private MetaStorageRepository metaStorageRepository;
    @Autowired private MetaStorageSyntheticRepository metaStorageSyntheticRepository;
    @Autowired private MetaStorageRegistryTxService metaStorageRegistryTxService;
    @Autowired private MetaStorageRegistryRepository metaStorageRegistryRepository;

    private static MetaStorageData.Record rec(String type, String recKey, String body) {
        return new MetaStorageData.Record(type, recKey, body);
    }

    private static MetaStorageRegistryParams descriptor(String desc) {
        final MetaStorageRegistryParams p = new MetaStorageRegistryParams();
        p.desc = desc;
        p.producer = "test-producer-1.0";
        p.recKeyFormat = "k-N";
        p.bodyFormat = "plain text";
        p.execContextId = 42L;
        p.function = "mh.asset.test";
        p.consumer = "list, take, delete";
        return p;
    }

    private MetaStorageRegistry descriptorOf(Long companyId, String metaTable, boolean production) {
        return metaStorageRegistryRepository.findByCompanyIdAndMetaTableAndProd(companyId, metaTable, production);
    }

    private List<MetaStorageData.Record> select(Long companyId, String type, boolean production) {
        return production
                ? metaStorageService.select(companyId, type, null)
                : metaStorageSyntheticService.select(companyId, type, null);
    }

    /** recKey + body pairs, type left out - a clone renames the type and must keep everything else. */
    private static List<String> keysAndBodies(List<MetaStorageData.Record> records) {
        return records.stream().map(r -> r.recKey() + "=" + r.body()).toList();
    }

    // ---------- inside a domain ----------

    @Test
    public void test_cloneInsideProductionCopiesRecordsAndDescriptorUnderTheNewName() {
        final Long companyId = SharedItEnv.uniqueLong();
        final Long otherCompanyId = SharedItEnv.uniqueLong();
        final String source = SharedItEnv.uniqueCode("clone-src");
        final String target = SharedItEnv.uniqueCode("clone-dst");

        // PHASE #1: a populated production table with a descriptor; the same name under another company
        metaStorageService.upsert(companyId, List.of(rec(source, "k-1", "b1"), rec(source, "k-2", "{\"x\":2}\n"), rec(source, "k-3", "b3")));
        metaStorageRegistryTxService.upsert(companyId, source, true, descriptor("what " + source + " is for"));
        metaStorageService.upsert(otherCompanyId, List.of(rec(source, "foreign", "not ours")));

        // PHASE #2: clone inside production
        final MetaStorageCloneService.CloneResult r = metaStorageCloneService.clone(companyId, source, true, target, true);
        assertNull(r.error(), "PHASE #2: " + r.error());
        assertTrue(r.ok());
        assertEquals(3, r.copied(), "PHASE #2: every record of the source");
        assertTrue(r.descriptorCopied(), "PHASE #2: the source had a descriptor");

        // PHASE #3: the target holds the same keys and bodies, byte for byte, under the target name
        final List<MetaStorageData.Record> cloned = select(companyId, target, true);
        assertEquals(keysAndBodies(select(companyId, source, true)), keysAndBodies(cloned), "PHASE #3: same keys, same bodies");
        assertTrue(cloned.stream().allMatch(c -> target.equals(c.type())), "PHASE #3: written under the target name");

        // PHASE #4: new records, not the source's rows renamed - fresh ids, fresh @Version
        final MetaStorage sourceRow = metaStorageRepository.findByNaturalKey(companyId, source, "k-1");
        final MetaStorage targetRow = metaStorageRepository.findByNaturalKey(companyId, target, "k-1");
        assertNotNull(sourceRow, "PHASE #4: the source record is still there");
        assertNotNull(targetRow, "PHASE #4: the target record exists");
        assertNotEquals(sourceRow.id, targetRow.id, "PHASE #4: a clone is a new record");
        assertEquals(0, targetRow.version, "PHASE #4: inserted, not updated");
        assertTrue(targetRow.gen > 0, "PHASE #4: the target carries a generation of its own");

        // PHASE #5: the descriptor followed the table, every field of it
        final MetaStorageRegistry d = descriptorOf(companyId, target, true);
        assertNotNull(d, "PHASE #5: the target got a descriptor");
        final MetaStorageRegistryParams p = d.getMetaStorageRegistryParams();
        assertEquals("what " + source + " is for", p.desc);
        assertEquals("test-producer-1.0", p.producer);
        assertEquals("k-N", p.recKeyFormat);
        assertEquals("plain text", p.bodyFormat);
        assertEquals(42L, p.execContextId);
        assertEquals("mh.asset.test", p.function);
        assertEquals("list, take, delete", p.consumer);
        assertNotNull(descriptorOf(companyId, source, true), "PHASE #5: and the source kept its own");

        // PHASE #6: nothing else moved - the synthetic store, the other company
        assertEquals(List.of(), metaStorageSyntheticService.listKeys(companyId, target), "PHASE #6: nothing in the synthetic store");
        assertEquals(List.of(), metaStorageService.listKeys(otherCompanyId, target), "PHASE #6: another company is not cloned into");
        assertEquals(List.of("foreign"), metaStorageService.listKeys(otherCompanyId, source), "PHASE #6: nor cloned from");
    }

    @Test
    public void test_cloneInsideSyntheticWithoutADescriptorLeavesTheTargetUndescribed() {
        final Long companyId = SharedItEnv.uniqueLong();
        final String source = SharedItEnv.uniqueCode("synth-src");
        final String target = SharedItEnv.uniqueCode("synth-dst");

        metaStorageSyntheticService.upsert(companyId, List.of(rec(source, "a", "1"), rec(source, "b", "2")));

        final MetaStorageCloneService.CloneResult r = metaStorageCloneService.clone(companyId, source, false, target, false);
        assertNull(r.error(), r.error());
        assertEquals(2, r.copied());
        assertFalse(r.descriptorCopied(), "no descriptor to copy");
        assertNull(descriptorOf(companyId, target, false), "and none invented");
        assertEquals(List.of("a=1", "b=2"), keysAndBodies(select(companyId, target, false)));
        assertEquals(List.of(), metaStorageService.listKeys(companyId, target), "the production store is not touched");
    }

    // ---------- between domains ----------

    @Test
    public void test_copyFromProductionIntoSyntheticUnderTheSameName() {
        final Long companyId = SharedItEnv.uniqueLong();
        final String table = SharedItEnv.uniqueCode("between");

        metaStorageService.upsert(companyId, List.of(rec(table, "k-1", "p1"), rec(table, "k-2", "p2")));
        metaStorageRegistryTxService.upsert(companyId, table, true, descriptor("production " + table));

        // PHASE #1: the same name in the other store is a different table, so it is a legal target
        final MetaStorageCloneService.CloneResult r = metaStorageCloneService.clone(companyId, table, true, table, false);
        assertNull(r.error(), "PHASE #1: " + r.error());
        assertEquals(2, r.copied());
        assertTrue(r.descriptorCopied());

        // PHASE #2: the records landed in MH_META_STORAGE_SYNTHETIC
        final MetaStorageSynthetic row = metaStorageSyntheticRepository.findByNaturalKey(companyId, table, "k-2");
        assertNotNull(row, "PHASE #2: the synthetic record exists");
        assertEquals("p2", row.body);
        assertEquals(List.of("k-1=p1", "k-2=p2"), keysAndBodies(select(companyId, table, false)));

        // PHASE #3: the synthetic store got a descriptor of its own, PROD=false
        final MetaStorageRegistry d = descriptorOf(companyId, table, false);
        assertNotNull(d, "PHASE #3: the synthetic table is described");
        assertFalse(d.prod);
        assertEquals("production " + table, d.getMetaStorageRegistryParams().desc);

        // PHASE #4: production is unchanged
        assertEquals(List.of("k-1=p1", "k-2=p2"), keysAndBodies(select(companyId, table, true)), "PHASE #4: the source is untouched");
        assertNotNull(descriptorOf(companyId, table, true), "PHASE #4: and keeps its descriptor");
    }

    @Test
    public void test_copyFromSyntheticIntoProductionUnderANewName() {
        final Long companyId = SharedItEnv.uniqueLong();
        final String source = SharedItEnv.uniqueCode("promote-src");
        final String target = SharedItEnv.uniqueCode("promote-dst");

        metaStorageSyntheticService.upsert(companyId, List.of(rec(source, "x", "s-x")));

        final MetaStorageCloneService.CloneResult r = metaStorageCloneService.clone(companyId, source, false, target, true);
        assertNull(r.error(), r.error());
        assertEquals(1, r.copied());
        assertFalse(r.descriptorCopied());

        assertEquals(List.of("x=s-x"), keysAndBodies(select(companyId, target, true)), "written into MH_META_STORAGE");
        assertEquals(List.of(), metaStorageSyntheticService.listKeys(companyId, target), "and only there");
        assertEquals(List.of("x"), metaStorageSyntheticService.listKeys(companyId, source), "the source stays where it was");
    }

    @Test
    public void test_aTableLargerThanOneRoundIsCopiedWhole() {
        final Long companyId = SharedItEnv.uniqueLong();
        final String source = SharedItEnv.uniqueCode("rounds-src");
        final String target = SharedItEnv.uniqueCode("rounds-dst");
        final int total = MetaStorageCloneUtils.CLONE_CHUNK_SIZE + 3;

        final List<MetaStorageData.Record> records = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            records.add(rec(source, String.format("k-%04d", i), "body-" + i));
        }
        assertEquals(total, metaStorageSyntheticService.upsert(companyId, records));

        final MetaStorageCloneService.CloneResult r = metaStorageCloneService.clone(companyId, source, false, target, false);
        assertNull(r.error(), r.error());
        assertEquals(total, r.copied(), "two rounds, nothing lost at the boundary");
        assertEquals(keysAndBodies(select(companyId, source, false)), keysAndBodies(select(companyId, target, false)));
    }

    // ---------- refusals: nothing is written ----------

    @Test
    public void test_aPopulatedTargetIsRefusedAndLeftAsItWas() {
        final Long companyId = SharedItEnv.uniqueLong();
        final String source = SharedItEnv.uniqueCode("busy-src");
        final String target = SharedItEnv.uniqueCode("busy-dst");

        metaStorageService.upsert(companyId, List.of(rec(source, "k-1", "new"), rec(source, "k-2", "new")));
        metaStorageService.upsert(companyId, List.of(rec(target, "k-1", "old")));

        final MetaStorageCloneService.CloneResult r = metaStorageCloneService.clone(companyId, source, true, target, true);
        assertNotNull(r.error());
        assertTrue(r.error().startsWith("01.952.060 "), r.error());
        assertFalse(r.ok());
        assertEquals(0, r.copied());

        assertEquals(List.of("k-1=old"), keysAndBodies(select(companyId, target, true)),
                "neither overwritten nor merged into");
    }

    @Test
    public void test_aLeftoverDescriptorOnAnEmptyTargetIsRefusedAndKept() {
        final Long companyId = SharedItEnv.uniqueLong();
        final String source = SharedItEnv.uniqueCode("stale-src");
        final String target = SharedItEnv.uniqueCode("stale-dst");

        metaStorageService.upsert(companyId, List.of(rec(source, "k-1", "b")));
        metaStorageRegistryTxService.upsert(companyId, source, true, descriptor("source"));
        metaStorageRegistryTxService.upsert(companyId, target, false, descriptor("leftover"));

        final MetaStorageCloneService.CloneResult r = metaStorageCloneService.clone(companyId, source, true, target, false);
        assertNotNull(r.error());
        assertTrue(r.error().startsWith("01.952.080 "), r.error());

        assertEquals(List.of(), metaStorageSyntheticService.listKeys(companyId, target), "no record written");
        final MetaStorageRegistry d = descriptorOf(companyId, target, false);
        assertNotNull(d);
        assertEquals("leftover", d.getMetaStorageRegistryParams().desc, "the leftover descriptor is not relabelled");
    }

    @Test
    public void test_cloningOntoItselfIsRefused() {
        final Long companyId = SharedItEnv.uniqueLong();
        final String table = SharedItEnv.uniqueCode("self");
        metaStorageService.upsert(companyId, List.of(rec(table, "k-1", "b")));

        final MetaStorageCloneService.CloneResult r = metaStorageCloneService.clone(companyId, table, true, table, true);
        assertNotNull(r.error());
        assertTrue(r.error().startsWith("01.952.020 "), r.error());
        assertEquals(List.of("k-1=b"), keysAndBodies(select(companyId, table, true)));
    }

    @Test
    public void test_anEmptySourceIsRefused() {
        final Long companyId = SharedItEnv.uniqueLong();
        final String source = SharedItEnv.uniqueCode("empty-src");
        final String target = SharedItEnv.uniqueCode("empty-dst");
        // a descriptor alone does not make a table
        metaStorageRegistryTxService.upsert(companyId, source, true, descriptor("orphan"));

        final MetaStorageCloneService.CloneResult r = metaStorageCloneService.clone(companyId, source, true, target, true);
        assertNotNull(r.error());
        assertTrue(r.error().startsWith("01.952.040 "), r.error());
        assertNull(descriptorOf(companyId, target, true), "the orphan descriptor is not copied either");
    }

    @Test
    public void test_aMalformedTargetNameIsRefusedBeforeAnythingIsWritten() {
        final Long companyId = SharedItEnv.uniqueLong();
        final String source = SharedItEnv.uniqueCode("named-src");
        metaStorageService.upsert(companyId, List.of(rec(source, "k-1", "b")));

        final MetaStorageCloneService.CloneResult r = metaStorageCloneService.clone(companyId, source, true, source + " copy", false);
        assertNotNull(r.error());
        assertTrue(r.error().startsWith("01.946.020 "), r.error());
        assertEquals(List.of(), metaStorageSyntheticService.listTypes(companyId), "nothing reached the synthetic store");
    }
}
