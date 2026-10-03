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
import ai.metaheuristic.ai.dispatcher.beans.MetaStorageRegistry;
import ai.metaheuristic.ai.dispatcher.repositories.MetaStorageRegistryRepository;
import ai.metaheuristic.api.data.meta_storage.MetaStorageRegistryParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Editing a meta table's description on the V3 harness - real Spring context, real H2, real
 * MH_META_STORAGE_REGISTRY.
 *
 * <p>No doubles, per RULE-NO-MOCKITO.md: what is asserted is what the registry holds afterwards -
 * whether CREATED_ON survived, whether the other fields of PARAMS survived, whether the other store's
 * descriptor was left alone - and only the real table can answer that.
 *
 * <p>❗ {@code companyId} comes from {@link SharedItEnv#uniqueLong()} - never {@code 1L} - and every
 * table name from {@link SharedItEnv#uniqueCode}.
 *
 * @author Serge
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
public class MetaStorageDescriptionServiceTest extends MhSharedItTest {

    @Autowired private MetaStorageDescriptionService metaStorageDescriptionService;
    @Autowired private MetaStorageService metaStorageService;
    @Autowired private MetaStorageSyntheticService metaStorageSyntheticService;
    @Autowired private MetaStorageRegistryTxService metaStorageRegistryTxService;
    @Autowired private MetaStorageRegistryRepository metaStorageRegistryRepository;

    private static MetaStorageData.Record rec(String type, String recKey) {
        return new MetaStorageData.Record(type, recKey, "body");
    }

    private MetaStorageRegistry descriptorOf(Long companyId, String metaTable, boolean production) {
        return metaStorageRegistryRepository.findByCompanyIdAndMetaTableAndProd(companyId, metaTable, production);
    }

    @Test
    public void test_registersADescriptorForAnUndescribedTable() {
        final Long companyId = SharedItEnv.uniqueLong();
        final String table = SharedItEnv.uniqueCode("desc-new");
        metaStorageService.upsert(companyId, List.of(rec(table, "k-1")));
        final long before = System.currentTimeMillis();

        final MetaStorageDescriptionService.DescriptionResult r =
                metaStorageDescriptionService.updateDescription(companyId, table, true, "  what this table is for \n");

        assertNull(r.error(), r.error());
        assertTrue(r.created(), "there was no descriptor");
        assertEquals("what this table is for", r.description(), "stored stripped");

        final MetaStorageRegistry d = descriptorOf(companyId, table, true);
        assertNotNull(d);
        assertEquals("what this table is for", d.getMetaStorageRegistryParams().desc);
        assertNull(d.getMetaStorageRegistryParams().producer, "nothing is invented for the fields nobody stated");
        assertTrue(d.createdOn >= before, "CREATED_ON stamped at registration");
    }

    @Test
    public void test_updatesTheDescriptionAndKeepsEverythingElseOfTheDescriptor() {
        final Long companyId = SharedItEnv.uniqueLong();
        final String table = SharedItEnv.uniqueCode("desc-upd");
        metaStorageService.upsert(companyId, List.of(rec(table, "k-1")));

        final MetaStorageRegistryParams p = new MetaStorageRegistryParams();
        p.desc = "old";
        p.producer = "producer-1.0";
        p.recKeyFormat = "k-N";
        p.bodyFormat = "json";
        p.execContextId = 42L;
        p.function = "mh.asset.writer";
        p.consumer = "list, take, delete";
        metaStorageRegistryTxService.upsert(companyId, table, true, p);
        final MetaStorageRegistry before = descriptorOf(companyId, table, true);
        assertNotNull(before);

        final MetaStorageDescriptionService.DescriptionResult r =
                metaStorageDescriptionService.updateDescription(companyId, table, true, "new");
        assertNull(r.error(), r.error());
        assertFalse(r.created(), "an existing descriptor was updated");

        final MetaStorageRegistry after = descriptorOf(companyId, table, true);
        assertNotNull(after);
        assertEquals(before.id, after.id, "the same descriptor, not a second one");
        assertEquals(before.createdOn, after.createdOn, "CREATED_ON is how old the table is - an edit does not move it");
        final MetaStorageRegistryParams ap = after.getMetaStorageRegistryParams();
        assertEquals("new", ap.desc);
        assertEquals("producer-1.0", ap.producer);
        assertEquals("k-N", ap.recKeyFormat);
        assertEquals("json", ap.bodyFormat);
        assertEquals(42L, ap.execContextId);
        assertEquals("mh.asset.writer", ap.function);
        assertEquals("list, take, delete", ap.consumer);
    }

    @Test
    public void test_editsOnlyTheDescriptorOfTheChosenStore() {
        final Long companyId = SharedItEnv.uniqueLong();
        final String table = SharedItEnv.uniqueCode("desc-store");
        metaStorageService.upsert(companyId, List.of(rec(table, "k-1")));
        metaStorageSyntheticService.upsert(companyId, List.of(rec(table, "k-1")));
        final MetaStorageRegistryParams p = new MetaStorageRegistryParams();
        p.desc = "production text";
        metaStorageRegistryTxService.upsert(companyId, table, true, p);

        final MetaStorageDescriptionService.DescriptionResult r =
                metaStorageDescriptionService.updateDescription(companyId, table, false, "synthetic text");
        assertNull(r.error(), r.error());
        assertTrue(r.created(), "the synthetic table had no descriptor of its own");

        assertEquals("synthetic text", descriptorOf(companyId, table, false).getMetaStorageRegistryParams().desc);
        assertEquals("production text", descriptorOf(companyId, table, true).getMetaStorageRegistryParams().desc,
                "the same name in the other store is another table, with its own descriptor");
    }

    @Test
    public void test_refusedForATableWithNoRecordsAndNoDescriptorIsCreated() {
        final Long companyId = SharedItEnv.uniqueLong();
        final String table = SharedItEnv.uniqueCode("desc-ghost");
        // records exist in the OTHER store only
        metaStorageSyntheticService.upsert(companyId, List.of(rec(table, "k-1")));

        final MetaStorageDescriptionService.DescriptionResult r =
                metaStorageDescriptionService.updateDescription(companyId, table, true, "describes nothing");

        assertNotNull(r.error());
        assertTrue(r.error().startsWith("01.955.020 "), r.error());
        assertNull(r.description());
        assertNull(descriptorOf(companyId, table, true), "no descriptor for a table that does not exist");
    }

    @Test
    public void test_refusedForABlankDescriptionAndNothingChanges() {
        final Long companyId = SharedItEnv.uniqueLong();
        final String table = SharedItEnv.uniqueCode("desc-blank");
        metaStorageService.upsert(companyId, List.of(rec(table, "k-1")));
        final MetaStorageRegistryParams p = new MetaStorageRegistryParams();
        p.desc = "kept";
        metaStorageRegistryTxService.upsert(companyId, table, true, p);

        final MetaStorageDescriptionService.DescriptionResult r =
                metaStorageDescriptionService.updateDescription(companyId, table, true, "   ");

        assertNotNull(r.error());
        assertTrue(r.error().startsWith("01.954.020 "), r.error());
        assertEquals("kept", descriptorOf(companyId, table, true).getMetaStorageRegistryParams().desc);
    }

    @Test
    public void test_refusedWhenTheDescriptorDoesNotParseAndItsPayloadIsLeftAsItWas() {
        final Long companyId = SharedItEnv.uniqueLong();
        final String table = SharedItEnv.uniqueCode("desc-broken");
        metaStorageService.upsert(companyId, List.of(rec(table, "k-1")));

        // a descriptor whose PARAMS are not a registry payload at all
        final MetaStorageRegistry broken = new MetaStorageRegistry();
        broken.companyId = companyId;
        broken.metaTable = table;
        broken.prod = true;
        broken.createdOn = System.currentTimeMillis();
        broken.setParams("this is not json");
        metaStorageRegistryRepository.save(broken);

        final MetaStorageDescriptionService.DescriptionResult r =
                metaStorageDescriptionService.updateDescription(companyId, table, true, "new");

        assertNotNull(r.error());
        assertTrue(r.error().startsWith("01.955.040 "), r.error());
        final MetaStorageRegistry after = descriptorOf(companyId, table, true);
        assertNotNull(after);
        assertEquals("this is not json", after.getParams(), "nothing that was in there has been overwritten");
    }
}
