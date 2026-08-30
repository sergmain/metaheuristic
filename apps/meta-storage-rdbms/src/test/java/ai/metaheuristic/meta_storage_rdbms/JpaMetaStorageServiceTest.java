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

package ai.metaheuristic.meta_storage_rdbms;

import ai.metaheuristic.meta_storage_rdbms.beans.MetaStorageRecord;
import ai.metaheuristic.meta_storage_rdbms.data.MetaRecordParams;
import ai.metaheuristic.meta_storage_rdbms.repositories.MetaStorageRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full Spring integration test on the V3 harness - one shared context and one H2 file DB for the
 * whole JVM run.
 *
 * <p>Same scenario as the SQLite module's {@code SqliteMetaStorageServiceTest}, deliberately, so the
 * two implementations are compared on identical behaviour rather than on prose. What differs is
 * everything underneath: JPQL instead of hand-written {@code ON CONFLICT}, the host's own database
 * instead of a file, one connection pool instead of two, and writes that join the caller's
 * transaction.
 *
 * <p>No doubles, per RULE-NO-MOCKITO.md: a stubbed repository could not express whether the unique
 * constraint holds, whether {@code @TableGenerator} allocated an id, whether a body survives the
 * version chain, or whether Liquibase created the table.
 *
 * <p>{@code companyId}-style identifiers come from {@link MetaStorageSharedItEnv#uniqueCode} - the
 * shared DB is isolated by unique names, not by teardown.
 *
 * @author Serge
 */
@SpringBootTest(classes = MetaStorageTestConfig.class)
@Execution(ExecutionMode.SAME_THREAD)
public class JpaMetaStorageServiceTest extends MetaStorageSharedItTest {

    @Autowired private MetaStorageSpi metaStorageSpi;
    @Autowired private MetaStorageRecordRepository metaStorageRecordRepository;
    @Autowired private DataSource dataSource;
    @Autowired private Map<String, DataSource> allDataSourceBeans;

    @Test
    public void test_upsertThenFetchByTypeThenFetchOne() {

        final String bucket = MetaStorageSharedItEnv.uniqueCode("bucket");

        // PHASE #1: three DTOs - two of type 'one', one of type 'two'
        final MetaRecordParams one1 = newRecord("one", "jane.doe@example.com", "Jane", "Doe", "jane.doe@example.com");
        final MetaRecordParams one2 = newRecord("one", "john.roe@example.com", "John", "Roe", "john.roe@example.com");
        final MetaRecordParams two1 = newRecord("two", "acme-corp", "Acme", null, null);

        // PHASE #2: store them
        final int rows = metaStorageSpi.upsert(bucket, List.of(one1, one2, two1));
        assertEquals(3, rows, "PHASE #2: all three records should have been written");

        // PHASE #3: select the list of DTOs with type=='one'
        final List<MetaRecordParams> typeOne = metaStorageSpi.fetch(bucket, "one", null);
        assertEquals(2, typeOne.size(), "PHASE #3: exactly the two type='one' records should come back");
        assertTrue(typeOne.stream().allMatch(p -> "one".equals(p.type)),
                "PHASE #3: the type filter must be applied by the database, not by the caller");
        // fetch() orders by recKey, so this ordering is part of the contract rather than an accident
        assertEquals("jane.doe@example.com", typeOne.get(0).recKey, "PHASE #3: first record by recKey order");
        assertEquals("john.roe@example.com", typeOne.get(1).recKey, "PHASE #3: second record by recKey order");

        final List<MetaRecordParams> typeTwo = metaStorageSpi.fetch(bucket, "two", null);
        assertEquals(1, typeTwo.size(), "PHASE #3: type='two' must not leak into type='one' and vice versa");

        // PHASE #4: query one specific DTO from db
        final List<MetaRecordParams> single = metaStorageSpi.fetch(bucket, "one", List.of("john.roe@example.com"));
        assertEquals(1, single.size(), "PHASE #4: exactly one record for one recKey");
        final MetaRecordParams actual = single.get(0);
        assertNotNull(actual, "PHASE #4: john.roe@example.com should be readable back");
        assertEquals("one", actual.type, "PHASE #4: john.roe@example.com.type");
        assertEquals("john.roe@example.com", actual.recKey, "PHASE #4: john.roe@example.com.recKey");
        assertEquals("John", actual.name, "PHASE #4: john.roe@example.com.name");
        assertEquals("Roe", actual.secondName, "PHASE #4: john.roe@example.com.secondName");
        assertEquals("john.roe@example.com", actual.email, "PHASE #4: john.roe@example.com.email");
        assertEquals(1, actual.version, "PHASE #4: body must round-trip through the v1 chain");

        // PHASE #5: a nullable field left unset must survive the round trip as null
        final List<MetaRecordParams> acme = metaStorageSpi.fetch(bucket, "two", List.of("acme-corp"));
        assertEquals(1, acme.size(), "PHASE #5: exactly one record for recKey 'acme-corp'");
        assertEquals("Acme", acme.get(0).name, "PHASE #5: acme-corp.name");
        assertNull(acme.get(0).secondName, "PHASE #5: acme-corp.secondName should stay null");

        // PHASE #6: re-upserting the same natural key updates in place instead of duplicating -
        // this is what makes a replayed batch safe
        one2.name = "Johnathan";
        final int rowsAgain = metaStorageSpi.upsert(bucket, List.of(one2));
        assertEquals(1, rowsAgain, "PHASE #6: one row affected");
        final List<MetaRecordParams> afterReplay = metaStorageSpi.fetch(bucket, "one", null);
        assertEquals(2, afterReplay.size(), "PHASE #6: still two records - the replay must not append a duplicate");
        assertEquals("Johnathan", afterReplay.get(1).name, "PHASE #6: john.roe@example.com.name was updated in place");

        // PHASE #7: buckets are isolated from each other
        final String otherBucket = MetaStorageSharedItEnv.uniqueCode("bucket");
        assertEquals(0, metaStorageSpi.fetch(otherBucket, "one", null).size(),
                "PHASE #7: a different bucket must not see this bucket's records");
    }

    @Test
    public void test_tableGeneratorAndOptimisticLocking() {

        final String bucket = MetaStorageSharedItEnv.uniqueCode("bucket");
        final MetaRecordParams p = newRecord("one", "id-check@example.com", "Ida", null, null);

        // PHASE #1: the id comes from META_STORAGE_RECORD_GEN_IDS, not from IDENTITY - which is what
        // makes the same DDL behave identically on all four engines
        metaStorageSpi.upsert(bucket, List.of(p));
        final MetaStorageRecord row = metaStorageRecordRepository.findByNaturalKey(bucket, "one", "id-check@example.com");
        assertNotNull(row, "PHASE #1: the row must exist");
        assertNotNull(row.id, "PHASE #1: @TableGenerator must have allocated an id");
        assertEquals(0, row.version, "PHASE #1: @Version starts at 0 on the first insert");

        // PHASE #2: an in-place update bumps @Version - optimistic locking is at the grain that
        // actually changes, one record to one row
        p.name = "Idamae";
        metaStorageSpi.upsert(bucket, List.of(p));
        final MetaStorageRecord updated = metaStorageRecordRepository.findByNaturalKey(bucket, "one", "id-check@example.com");
        assertNotNull(updated, "PHASE #2: the row must still exist");
        assertEquals(row.id, updated.id, "PHASE #2: the same row was updated, not replaced");
        assertEquals(1, updated.version, "PHASE #2: @Version bumped by the in-place update");

        // PHASE #3: the generation stamp advances on write, so a cached key list can be invalidated
        // from the store's own state rather than from an operator bumping a seed by hand
        assertTrue(updated.gen > row.gen, "PHASE #3: gen advanced on the second write");
    }

    @Test
    public void test_exactlyOneDataSourceBeanInTheContext() {

        // ❗ Trivially true here, and that is the point: with no embedded engine there is no second
        // pool to keep out of the context. The assertion stays as a guard - the moment someone adds
        // a DataSource bean, DataSourceAutoConfiguration backs off and JPA, the transaction manager
        // and SpringLiquibase are left resolving DataSource by type with two candidates.
        assertEquals(1, allDataSourceBeans.size(),
                "exactly one DataSource bean. found: " + allDataSourceBeans.keySet());
        assertNotNull(dataSource, "the auto-configured DataSource must be present");
    }

    private static MetaRecordParams newRecord(String type, String recKey, String name,
                                              String secondName, String email) {
        final MetaRecordParams p = new MetaRecordParams();
        p.type = type;
        p.recKey = recKey;
        p.name = name;
        p.secondName = secondName;
        p.email = email;
        return p;
    }
}
