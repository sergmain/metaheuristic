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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.MhSharedItTest;
import ai.metaheuristic.ai.SharedItEnv;
import ai.metaheuristic.ai.dispatcher.beans.MetaStorage;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionRegisterService;
import ai.metaheuristic.ai.dispatcher.repositories.MetaStorageRepository;
import ai.metaheuristic.api.dispatcher.InternalFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Meta storage on the V3 harness - real Spring context, real H2, real Liquibase-created table.
 *
 * <p>No doubles, per RULE-NO-MOCKITO.md: a stubbed repository could not express whether the UNIQUE
 * constraint on {@code (COMPANY_ID, TYPE, REC_KEY)} actually holds, whether {@code @TableGenerator}
 * allocated an id from {@code mh_gen_ids}, whether {@code @Version} bumps on an in-place update, or
 * whether Liquibase created {@code MH_META_STORAGE} at all.
 *
 * <p>❗ {@code companyId} comes from {@link SharedItEnv#uniqueLong()} - never {@code 1L}, which is
 * reserved for the MH management company and triggers special logic. Isolation on the shared DB is
 * by unique identifiers, not by teardown.
 *
 * @author Serge
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
public class MetaStorageServiceTest extends MhSharedItTest {

    @Autowired private MetaStorageService metaStorageService;
    @Autowired private MetaStorageRepository metaStorageRepository;
    @Autowired private InternalFunctionRegisterService internalFunctionRegisterService;

    @Test
    public void test_upsertThenSelectByTypeThenSelectOne() {

        final Long companyId = SharedItEnv.uniqueLong();

        // PHASE #1: three records - two of type 'one', one of type 'two'. Bodies are opaque strings;
        // MH never parses them, so the JSON here is only the caller's own choice of encoding.
        final MetaStorageData.Record one1 = new MetaStorageData.Record(
                "one", "jane.doe@example.com", "{\"name\":\"Jane\",\"secondName\":\"Doe\"}");
        final MetaStorageData.Record one2 = new MetaStorageData.Record(
                "one", "john.roe@example.com", "{\"name\":\"John\",\"secondName\":\"Roe\"}");
        final MetaStorageData.Record two1 = new MetaStorageData.Record(
                "two", "acme-corp", "just a plain string, not JSON at all");

        // PHASE #2: store them
        final int rows = metaStorageService.upsert(companyId, List.of(one1, one2, two1));
        assertEquals(3, rows, "PHASE #2: all three records should have been written");

        // PHASE #3: select the list of records with type=='one'
        final List<MetaStorageData.Record> typeOne = metaStorageService.select(companyId, "one", null);
        assertEquals(2, typeOne.size(), "PHASE #3: exactly the two type='one' records should come back");
        assertTrue(typeOne.stream().allMatch(r -> "one".equals(r.type())),
                "PHASE #3: the type filter must be applied by the database, not by the caller");
        // select() orders by recKey, so this ordering is part of the contract rather than an accident
        assertEquals("jane.doe@example.com", typeOne.get(0).recKey(), "PHASE #3: first record by recKey order");
        assertEquals("john.roe@example.com", typeOne.get(1).recKey(), "PHASE #3: second record by recKey order");

        final List<MetaStorageData.Record> typeTwo = metaStorageService.select(companyId, "two", null);
        assertEquals(1, typeTwo.size(), "PHASE #3: type='two' must not leak into type='one' and vice versa");

        // PHASE #4: query one specific record
        final List<MetaStorageData.Record> single = metaStorageService.select(companyId, "one", List.of("john.roe@example.com"));
        assertEquals(1, single.size(), "PHASE #4: exactly one record for one recKey");
        assertEquals("one", single.get(0).type(), "PHASE #4: john.roe@example.com.type");
        assertEquals("john.roe@example.com", single.get(0).recKey(), "PHASE #4: john.roe@example.com.recKey");
        assertEquals("{\"name\":\"John\",\"secondName\":\"Roe\"}", single.get(0).body(),
                "PHASE #4: body must round-trip byte-for-byte - MH stores it, it does not reformat it");

        // PHASE #5: a non-JSON body survives untouched, which is the point of BODY being opaque
        final List<MetaStorageData.Record> acme = metaStorageService.select(companyId, "two", List.of("acme-corp"));
        assertEquals(1, acme.size(), "PHASE #5: exactly one record for recKey 'acme-corp'");
        assertEquals("just a plain string, not JSON at all", acme.get(0).body(),
                "PHASE #5: MH must not require a body to be JSON");

        // PHASE #6: re-upserting the same natural key updates in place instead of duplicating -
        // this is what makes a replayed batch safe
        final MetaStorageData.Record one2Updated = new MetaStorageData.Record(
                "one", "john.roe@example.com", "{\"name\":\"Johnathan\",\"secondName\":\"Roe\"}");
        final int rowsAgain = metaStorageService.upsert(companyId, List.of(one2Updated));
        assertEquals(1, rowsAgain, "PHASE #6: one row affected");
        final List<MetaStorageData.Record> afterReplay = metaStorageService.select(companyId, "one", null);
        assertEquals(2, afterReplay.size(), "PHASE #6: still two records - the replay must not append a duplicate");
        assertEquals("{\"name\":\"Johnathan\",\"secondName\":\"Roe\"}", afterReplay.get(1).body(),
                "PHASE #6: john.roe@example.com body was updated in place");

        // PHASE #7: companies are isolated from each other
        final Long otherCompanyId = SharedItEnv.uniqueLong();
        assertEquals(0, metaStorageService.select(otherCompanyId, "one", null).size(),
                "PHASE #7: a different companyId must not see this company's records");

        // PHASE #8: the store enumerates its own types - no registry needed
        assertEquals(List.of("one", "two"), metaStorageService.listTypes(companyId),
                "PHASE #8: DISTINCT TYPE for this company");

        // PHASE #9: key list without bodies - the selection step that feeds a batch splitter
        assertEquals(List.of("jane.doe@example.com", "john.roe@example.com"),
                metaStorageService.listKeys(companyId, "one"), "PHASE #9: recKeys only, ordered");
    }

    @Test
    public void test_tableGeneratorAndOptimisticLockingAndGeneration() {

        final Long companyId = SharedItEnv.uniqueLong();
        final String recKey = SharedItEnv.uniqueCode("id-check") + "@example.com";

        // PHASE #1: the id comes from mh_gen_ids via @TableGenerator, not from IDENTITY - which is
        // what makes the same DDL behave identically on H2, MySQL, MariaDB, PostgreSQL and derby
        metaStorageService.upsert(companyId, List.of(new MetaStorageData.Record("one", recKey, "body-1")));
        final MetaStorage row = metaStorageRepository.findByNaturalKey(companyId, "one", recKey);
        assertNotNull(row, "PHASE #1: the row must exist");
        assertNotNull(row.id, "PHASE #1: @TableGenerator must have allocated an id");
        assertEquals(0, row.version, "PHASE #1: @Version starts at 0 on the first insert");
        final long gen1 = row.gen;

        // PHASE #2: an in-place update bumps @Version - optimistic locking is at the grain that
        // actually changes, one record to one row
        metaStorageService.upsert(companyId, List.of(new MetaStorageData.Record("one", recKey, "body-2")));
        final MetaStorage updated = metaStorageRepository.findByNaturalKey(companyId, "one", recKey);
        assertNotNull(updated, "PHASE #2: the row must still exist");
        assertEquals(row.id, updated.id, "PHASE #2: the same row was updated, not replaced");
        assertEquals(1, updated.version, "PHASE #2: @Version bumped by the in-place update");
        assertEquals("body-2", updated.body, "PHASE #2: body replaced");

        // PHASE #3: GEN advances on write, so a cached key list can be invalidated from the store's
        // own state rather than from an operator bumping a seed by hand
        assertTrue(updated.gen > gen1, "PHASE #3: gen advanced on the second write");
        assertEquals(updated.gen, metaStorageService.generation(companyId, "one"), "PHASE #3: generation() reports it");
        assertEquals(0L, metaStorageService.generation(companyId, "never-written"),
                "PHASE #3: a type never written has generation 0");
    }

    @Test
    public void test_metaStorageFunctionIsRegistered() {

        // The internal Function is discovered by InternalFunctionRegisterService from the context,
        // so this asserts the wiring a .mhsc depends on when it names mh.meta-storage.
        final InternalFunction fn = internalFunctionRegisterService.get(Consts.MH_META_STORAGE_FUNCTION);
        assertNotNull(fn, "mh.meta-storage must be registered as an internal function");
        assertEquals(Consts.MH_META_STORAGE_FUNCTION, fn.getCode(), "getCode()");
        assertEquals("mh.meta-storage", Consts.MH_META_STORAGE_FUNCTION, "the function code is a stable contract");
    }
}
