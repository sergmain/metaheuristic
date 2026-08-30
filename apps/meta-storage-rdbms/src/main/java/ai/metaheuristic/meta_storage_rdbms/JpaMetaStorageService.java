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
import ai.metaheuristic.meta_storage_rdbms.json.MetaRecordParamsUtils;
import ai.metaheuristic.meta_storage_rdbms.repositories.MetaStorageRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link MetaStorageSpi} on the MAIN datasource, through JPA - the option that adds no engine.
 *
 * <p>Same table shape and same semantics as the SQLite implementation, but every query is JPQL, so
 * Hibernate emits whatever H2, MySQL, MariaDB or PostgreSQL needs. Hibernate 7.4 ships a dialect for
 * all four and none for SQLite, which is why the embedded-engine implementation next door has to
 * hand-write its SQL.
 *
 * <p>What this buys over the embedded option, beyond the dialects: writes participate in the
 * caller's transaction, {@code @Version} optimistic locking is enforced by the same layer as
 * everything else, the pool is monitored like every other pool, and there is one backup procedure
 * rather than two. What it gives up: the meta-storage rows share a database with governance data,
 * so isolating their volume means a separate schema or database rather than a separate file.
 *
 * <p>Non-transactional orchestrator per SPRING-TX-RULES.md; writes go through
 * {@link JpaMetaStorageTxService}.
 *
 * <p>Error code prefix: {@code 01.946.} (unique to this class).
 *
 * @author Serge
 */
@Slf4j
@RequiredArgsConstructor
public class JpaMetaStorageService implements MetaStorageSpi {

    /**
     * IN-clause chunk size. Every engine has a parameter-count ceiling and they differ, so the
     * chunking is what keeps a caller passing a whole key list at once degrading instead of failing.
     */
    public static final int MAX_KEYS_PER_QUERY = 500;

    private final MetaStorageRecordRepository metaStorageStubRepository;
    private final JpaMetaStorageTxService jpaMetaStorageTxService;

    @Override
    public List<MetaRecordParams> fetch(String bucket, String type, @Nullable List<String> recKeys) {
        if (recKeys == null || recKeys.isEmpty()) {
            return toParams(metaStorageStubRepository.findAllByBucketAndType(bucket, type));
        }
        final List<MetaRecordParams> result = new ArrayList<>(recKeys.size());
        for (int from = 0; from < recKeys.size(); from += MAX_KEYS_PER_QUERY) {
            final List<String> chunk = recKeys.subList(from, Math.min(from + MAX_KEYS_PER_QUERY, recKeys.size()));
            result.addAll(toParams(metaStorageStubRepository.findAllByBucketAndTypeAndRecKeys(bucket, type, chunk)));
        }
        return result;
    }

    @Override
    public int upsert(String bucket, List<MetaRecordParams> records) {
        if (records.isEmpty()) {
            return 0;
        }
        return jpaMetaStorageTxService.upsert(bucket, records, nextGeneration(), System.currentTimeMillis());
    }

    /** Key list only, for the selection step that feeds the batch splitter. Bodies stay unread. */
    public List<String> listKeys(String bucket, String type) {
        return metaStorageStubRepository.findRecKeysByBucketAndType(bucket, type);
    }

    public long nextGeneration() {
        final Long max = metaStorageStubRepository.findMaxGenGlobally();
        return (max == null ? 0L : max) + 1L;
    }

    /** Current generation for one (bucket, type). Zero when the type has never been written. */
    public long generation(String bucket, String type) {
        final Long max = metaStorageStubRepository.findMaxGen(bucket, type);
        return max == null ? 0L : max;
    }

    private static List<MetaRecordParams> toParams(List<MetaStorageRecord> rows) {
        final List<MetaRecordParams> result = new ArrayList<>(rows.size());
        for (MetaStorageRecord row : rows) {
            if (row.body == null) {
                throw new IllegalStateException(
                        "01.946.020 BODY is null, which the NOT NULL constraint should have prevented, recKey: " + row.recKey);
            }
            result.add(MetaRecordParamsUtils.BASE_JSON_UTILS.to(row.body));
        }
        return result;
    }
}
