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

import ai.metaheuristic.meta_storage.data.MetaRecordParams;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against a REAL SQLite file in a per-test temp directory - real driver, real SQL, real
 * ON CONFLICT, real JSON round-trip through the versioning chain.
 *
 * <p>No doubles of any kind, per RULE-NO-MOCKITO.md: a stubbed store could not express the things
 * worth asserting here - that the unique constraint actually holds, that a body survives
 * serialize/deserialize through {@code MetaRecordParamsUtils}, or that a type filter is applied by
 * the database rather than by the test.
 *
 * @author Serge
 */
public class SqliteMetaStorageServiceTest {

    private static final String BUCKET = "bucket-42";

    private HikariDataSource dataSource;
    private SqliteMetaStorageService service;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) {
        final Path dbPath = tempDir.resolve("meta-storage-test.sqlite");
        final HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setMaximumPoolSize(1);
        cfg.setPoolName("meta-storage-test");
        cfg.setAutoCommit(true);
        dataSource = new HikariDataSource(cfg);

        service = new SqliteMetaStorageService(dataSource);
        service.initSchema();
    }

    @AfterEach
    public void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    public void test_upsertThenFetchByTypeThenFetchOne() {

        // PHASE #1: three DTOs - two of type 'one', one of type 'two'
        final MetaRecordParams one1 = newRecord("one", "jane.doe@example.com", "Jane", "Doe", "jane.doe@example.com");
        final MetaRecordParams one2 = newRecord("one", "john.roe@example.com", "John", "Roe", "john.roe@example.com");
        final MetaRecordParams two1 = newRecord("two", "acme-corp", "Acme", null, null);

        // PHASE #2: store them
        final int rows = service.upsert(BUCKET, List.of(one1, one2, two1));
        assertEquals(3, rows, "PHASE #2: all three records should have been written");

        // PHASE #3: select the list of DTOs with type=='one'
        final List<MetaRecordParams> typeOne = service.fetch(BUCKET, "one", null);
        assertEquals(2, typeOne.size(), "PHASE #3: exactly the two type='one' records should come back");
        assertTrue(typeOne.stream().allMatch(p -> "one".equals(p.type)),
                "PHASE #3: the type filter must be applied by the database, not by the caller");
        // fetch() orders by recKey, so this ordering is part of the contract rather than an accident
        assertEquals("jane.doe@example.com", typeOne.get(0).recKey, "PHASE #3: first record by recKey order");
        assertEquals("john.roe@example.com", typeOne.get(1).recKey, "PHASE #3: second record by recKey order");

        final List<MetaRecordParams> typeTwo = service.fetch(BUCKET, "two", null);
        assertEquals(1, typeTwo.size(), "PHASE #3: type='two' must not leak into type='one' and vice versa");

        // PHASE #4: query one specific DTO from db
        final List<MetaRecordParams> single = service.fetch(BUCKET, "one", List.of("john.roe@example.com"));
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
        final List<MetaRecordParams> acme = service.fetch(BUCKET, "two", List.of("acme-corp"));
        assertEquals(1, acme.size(), "PHASE #5: exactly one record for recKey 'acme-corp'");
        assertEquals("Acme", acme.get(0).name, "PHASE #5: acme-corp.name");
        assertEquals(null, acme.get(0).secondName, "PHASE #5: acme-corp.secondName should stay null");

        // PHASE #6: re-upserting the same natural key updates in place instead of duplicating -
        // this is what makes a replayed batch safe
        one2.name = "Johnathan";
        final int rowsAgain = service.upsert(BUCKET, List.of(one2));
        assertEquals(1, rowsAgain, "PHASE #6: one row affected");
        final List<MetaRecordParams> afterReplay = service.fetch(BUCKET, "one", null);
        assertEquals(2, afterReplay.size(), "PHASE #6: still two records - the replay must not append a duplicate");
        assertEquals("Johnathan", afterReplay.get(1).name, "PHASE #6: john.roe@example.com.name was updated in place");

        // PHASE #7: the generation stamp advances on write, so a cached key list can be invalidated
        // from the store's own state rather than from an operator bumping a seed by hand
        assertTrue(service.generation(BUCKET, "one") > service.generation(BUCKET, "two"),
                "PHASE #7: type='one' was written more recently than type='two'");
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
