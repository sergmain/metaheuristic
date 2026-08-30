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
import ai.metaheuristic.meta_storage.json.MetaRecordParamsUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * SQLite-backed {@link MetaStorageSpi}.
 *
 * <p><b>One table for every entity kind.</b> {@code TYPE} is a column value, so a new kind of thing
 * - contact, campaign, email-send - is a new string and never DDL. That is the runtime-schema
 * property the whole design rests on, and it is the reason this class knows nothing about what a
 * contact is.
 *
 * <p><b>BODY is the system of record.</b> {@code TYPE} and {@code REC_KEY} are a projection of two
 * body fields into indexed columns; drop the columns and they can be recomputed from the bodies.
 * Reads go through the versioning chain ({@link MetaRecordParamsUtils}), so a row written under an
 * older version is upgraded on the way out and the caller only ever sees the version-less class.
 *
 * <p><b>Serialisation.</b> SQLite permits one writer. The connection pool is pinned to a single
 * connection in {@link MetaStorageConfig}, so concurrent {@code upsert} calls queue on the pool
 * rather than colliding with SQLITE_BUSY. Callers do not see this.
 *
 * <p>Error code prefix: {@code 01.940.} (unique to this class).
 *
 * @author Serge
 */
@Slf4j
public class SqliteMetaStorageService implements MetaStorageSpi {

    /**
     * IN-clause chunk size. SQLite's default SQLITE_MAX_VARIABLE_NUMBER is 32766 on modern builds,
     * but the batch sizes this store is fed are 10-100 keys, so the chunking almost never engages.
     * It exists so that a caller passing a whole key list at once degrades instead of failing.
     */
    public static final int MAX_KEYS_PER_QUERY = 500;

    /**
     * Fail fast rather than block. A caller that already holds a connection from the MAIN pool -
     * anything inside {@code @Transactional} - and then waits here holds that main connection for
     * the whole wait. Hikari's 30s default would make a slow meta-storage write into main-pool
     * starvation; 3s turns it into an error the caller can see.
     */
    public static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(3);

    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    /**
     * ❗ The pool is built and owned HERE, not published as a Spring {@code DataSource} bean.
     *
     * <p>{@code DataSourceAutoConfiguration} is {@code @ConditionalOnMissingBean(DataSource.class)},
     * and {@code EntityManagerFactory}, the JPA transaction manager and {@code SpringLiquibase} all
     * resolve {@code DataSource} BY TYPE. A second bean of that type either makes Boot skip
     * configuring the real database, or leaves those three with two candidates and no rule for
     * choosing - and the bad outcome is not a startup failure but Liquibase running the main
     * changelog into the SQLite file.
     *
     * <p>Keeping the pool private means the context has exactly one {@code DataSource} bean, so no
     * {@code @Primary} and no qualifier is needed anywhere and a consumer cannot wire this one by
     * accident.
     */
    public SqliteMetaStorageService(MetaStorageProperties props) {
        final Path dbPath = Path.of(props.getDbPath()).toAbsolutePath();
        // SQLite creates the database FILE but never its parent directory - a missing parent comes
        // back as SQLITE_CANTOPEN from inside pool initialisation, which reads like a permissions
        // problem rather than a missing mkdir. Creating it here keeps a configured db-path working
        // wherever it points.
        final Path parent = dbPath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            }
            catch (IOException e) {
                throw new IllegalStateException(
                        "01.940.005 Can't create dir for the meta storage db: " + parent, e);
            }
        }
        final HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:" + dbPath);
        cfg.setDriverClassName("org.sqlite.JDBC");
        // SQLite permits one writer. A bigger pool converts a short in-pool wait into SQLITE_BUSY
        // surfacing in the caller, so serialisation happens here and callers never see it.
        cfg.setMaximumPoolSize(1);
        cfg.setPoolName("meta-storage");
        cfg.setAutoCommit(true);
        cfg.setConnectionTimeout(CONNECTION_TIMEOUT.toMillis());
        this.dataSource = new HikariDataSource(cfg);
        this.jdbcTemplate = new JdbcTemplate(this.dataSource);
        log.info("01.940.010 meta storage db: {}", dbPath);
    }

    @PreDestroy
    public void close() {
        dataSource.close();
        log.info("01.940.080 meta storage pool closed");
    }

    /**
     * Creates the single table if it is absent. Idempotent, so it is safe on every startup.
     *
     * <p>{@code json_valid(BODY)} is the one thing the store validates, and note what it is NOT: it
     * has no idea what fields a body should carry. Generic validation with zero type knowledge.
     */
    public void initSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS META_RECORD (
                  ID          INTEGER PRIMARY KEY AUTOINCREMENT,
                  BUCKET      TEXT    NOT NULL,
                  TYPE        TEXT    NOT NULL,
                  REC_KEY     TEXT    NOT NULL,
                  BODY        TEXT    NOT NULL CHECK (json_valid(BODY)),
                  GEN         INTEGER NOT NULL,
                  UPDATED_AT  INTEGER NOT NULL,
                  UNIQUE (BUCKET, TYPE, REC_KEY)
                )""");
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS IX_META_RECORD_TYPE ON META_RECORD(BUCKET, TYPE, GEN)");
        log.info("01.940.020 META_RECORD schema is ready");
    }

    @Override
    public List<MetaRecordParams> fetch(String bucket, String type, @Nullable List<String> recKeys) {
        if (recKeys == null || recKeys.isEmpty()) {
            return jdbcTemplate.query(
                    "SELECT BODY FROM META_RECORD WHERE BUCKET=? AND TYPE=? ORDER BY REC_KEY",
                    (rs, i) -> toParams(rs.getString(1)),
                    bucket, type);
        }
        final List<MetaRecordParams> result = new ArrayList<>(recKeys.size());
        for (int from = 0; from < recKeys.size(); from += MAX_KEYS_PER_QUERY) {
            final List<String> chunk = recKeys.subList(from, Math.min(from + MAX_KEYS_PER_QUERY, recKeys.size()));
            final String placeholders = String.join(",", chunk.stream().map(k -> "?").toList());
            final Object[] args = new Object[chunk.size() + 2];
            args[0] = bucket;
            args[1] = type;
            for (int i = 0; i < chunk.size(); i++) {
                args[i + 2] = chunk.get(i);
            }
            result.addAll(jdbcTemplate.query(
                    "SELECT BODY FROM META_RECORD WHERE BUCKET=? AND TYPE=? AND REC_KEY IN (" + placeholders + ") ORDER BY REC_KEY",
                    (rs, i) -> toParams(rs.getString(1)),
                    args));
        }
        return result;
    }

    @Override
    public int upsert(String bucket, List<MetaRecordParams> records) {
        if (records.isEmpty()) {
            return 0;
        }
        final long now = System.currentTimeMillis();
        final long gen = nextGeneration();

        // ON CONFLICT on the natural key is what makes a replayed batch harmless.
        final int[] counts = jdbcTemplate.batchUpdate("""
                INSERT INTO META_RECORD (BUCKET, TYPE, REC_KEY, BODY, GEN, UPDATED_AT)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (BUCKET, TYPE, REC_KEY) DO UPDATE SET
                  BODY = excluded.BODY, GEN = excluded.GEN, UPDATED_AT = excluded.UPDATED_AT""",
                records.stream().<Object[]>map(toRow(bucket, gen, now)).toList());

        int total = 0;
        for (int c : counts) {
            total += Math.max(c, 0);
        }
        log.info("01.940.040 upsert bucket={}, records={}, rows={}, gen={}", bucket, records.size(), total, gen);
        return total;
    }

    /**
     * Row mapper as a function of the invariant part of the batch, so the per-record lambda stays
     * side-effect free - the shape RULE-NO-MOCKITO.md §3.5 prefers.
     */
    private static Function<MetaRecordParams, Object[]> toRow(String bucket, long gen, long now) {
        return p -> {
            p.checkIntegrity();
            return new Object[]{bucket, p.type, p.recKey, MetaRecordParamsUtils.BASE_JSON_UTILS.toString(p), gen, now};
        };
    }

    /**
     * Monotonic per-store stamp.
     *
     * <p>Under mutation an index entry can be STALE rather than merely missing: two writes to one
     * record can reach a downstream indexer out of order and leave it permanently wrong. The
     * generation is what lets a late arrival be discarded, and it is also the value a {@code .mhsc}
     * feeds into the cache-busting slot so a cached key list is invalidated by the store's own state
     * instead of by an operator remembering to bump a seed.
     */
    public long nextGeneration() {
        final Long max = jdbcTemplate.queryForObject("SELECT coalesce(max(GEN), 0) FROM META_RECORD", Long.class);
        return (max == null ? 0L : max) + 1L;
    }

    /** Current generation for one (bucket, type). Zero when the type has never been written. */
    public long generation(String bucket, String type) {
        final Long max = jdbcTemplate.queryForObject(
                "SELECT coalesce(max(GEN), 0) FROM META_RECORD WHERE BUCKET=? AND TYPE=?", Long.class, bucket, type);
        return max == null ? 0L : max;
    }

    private static MetaRecordParams toParams(@Nullable String body) {
        if (body == null) {
            throw new IllegalStateException("01.940.060 BODY is null, which the NOT NULL constraint should have prevented");
        }
        return MetaRecordParamsUtils.BASE_JSON_UTILS.to(body);
    }
}
