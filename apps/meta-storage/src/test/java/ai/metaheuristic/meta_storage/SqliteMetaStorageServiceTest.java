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

package ai.metaheuristic.meta_storage;

import ai.metaheuristic.meta_storage.beans.MetaStorageStub;
import ai.metaheuristic.meta_storage.data.MetaRecordParams;
import ai.metaheuristic.meta_storage.repositories.MetaStorageStubRepository;
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
 * Full Spring integration test on the V3 harness - one shared context, one H2 file DB, one SQLite
 * file, for the whole JVM run.
 *
 * <p>Two things are proved here and neither is reachable without a real context:
 * <ol>
 *   <li>the meta storage works - upsert, select by type, fetch one, idempotent replay;</li>
 *   <li>❗ it coexists with a Liquibase-migrated, Hibernate-mapped MAIN datasource. Exactly ONE
 *       {@code DataSource} bean exists, so JPA and Liquibase bind unambiguously while the SQLite
 *       pool stays private to the service.</li>
 * </ol>
 *
 * <p>No doubles of any kind, per RULE-NO-MOCKITO.md: a stubbed store could not express whether the
 * unique constraint actually holds, whether a body survives the version chain, whether Liquibase
 * really created the stub table, or whether the two datasources collide at context startup - which
 * is the whole question this class exists to answer.
 *
 * <p>{@code companyId} comes from {@link MetaStorageSharedItEnv#uniqueLong()} - never {@code 1L},
 * which is reserved for the MH management company and triggers special logic.
 *
 * @author Serge
 */
@SpringBootTest(classes = MetaStorageTestConfig.class)
@Execution(ExecutionMode.SAME_THREAD)
public class SqliteMetaStorageServiceTest extends MetaStorageSharedItTest {

    @Autowired private MetaStorageSpi metaStorageSpi;
    @Autowired private MetaStorageStubRepository metaStorageStubRepository;
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
    public void test_liquibaseStubTableAndJpaRepository() {

        final Long companyId = MetaStorageSharedItEnv.uniqueLong();
        final String code = MetaStorageSharedItEnv.uniqueCode("stub");

        // PHASE #1: the table exists only because Liquibase created it on the MAIN datasource
        assertNull(metaStorageStubRepository.findByCompanyIdAndCode(companyId, code),
                "PHASE #1: nothing stored under this companyId/code yet");

        // PHASE #2: JPA write through the repository, on the MAIN datasource
        final MetaStorageStub stub = new MetaStorageStub();
        stub.companyId = companyId;
        stub.code = code;
        stub.params = "{\"version\":1}";
        stub.createdOn = System.currentTimeMillis();
        final MetaStorageStub saved = metaStorageStubRepository.save(stub);
        assertNotNull(saved.id, "PHASE #2: IDENTITY must have assigned an id");
        assertEquals(0, saved.version, "PHASE #2: @Version starts at 0 on the first insert");

        // PHASE #3: read it back
        final MetaStorageStub found = metaStorageStubRepository.findByCompanyIdAndCode(companyId, code);
        assertNotNull(found, "PHASE #3: the row written in PHASE #2 must be readable");
        assertEquals(code, found.code, "PHASE #3: stub.code");
        assertEquals(companyId, found.companyId, "PHASE #3: stub.companyId");

        // PHASE #4: global-scope reads are filtered to this test's own companyId - harness §0.4.6
        final List<String> codes = metaStorageStubRepository.findCodesByCompanyId(companyId);
        assertEquals(List.of(code), codes, "PHASE #4: exactly this test's own row");
    }

    @Test
    public void test_exactlyOneDataSourceBeanInTheContext() {

        // ❗ The whole point of keeping the SQLite pool private. Boot's DataSourceAutoConfiguration
        // is @ConditionalOnMissingBean(DataSource.class), and JPA, the transaction manager and
        // SpringLiquibase all resolve DataSource BY TYPE. A second bean of that type either
        // suppresses the real database or leaves those three choosing blindly - and the bad outcome
        // is Liquibase running the main changelog into the SQLite file, which is data corruption
        // rather than a startup error. This assertion is what stops that regression.
        assertEquals(1, allDataSourceBeans.size(),
                "exactly one DataSource bean; the SQLite pool must stay private to the service. found: "
                        + allDataSourceBeans.keySet());

        // And the one that IS a bean is H2 - the main store - not SQLite.
        assertNotNull(dataSource, "the auto-configured DataSource must be present");
        assertTrue(dataSource.getClass().getSimpleName().contains("Hikari"),
                "the main DataSource is Hikari-pooled, same as MH's");
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
