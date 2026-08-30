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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Pure {@code @Transactional} write methods for the JPA meta storage, per SPRING-TX-RULES.md:
 * {@code *TxService} owns the transaction, {@code *Service} orchestrates without one.
 *
 * <p>❗ Unlike the SQLite implementation, a write here PARTICIPATES in the caller's transaction. That
 * is the property a second engine cannot offer at any price: the meta-storage write and any other
 * write on this datasource commit or roll back together, so the dual-write window does not exist.
 *
 * <p>Error code prefix: {@code 01.945.} (unique to this class).
 *
 * @author Serge
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JpaMetaStorageTxService {

    private final MetaStorageRecordRepository metaStorageStubRepository;

    /**
     * Insert-or-update on the natural key.
     *
     * <p>JPQL has no {@code ON CONFLICT}, so this is find-then-write - which is what makes it
     * portable across all four dialects rather than the two that accept SQLite's syntax.
     *
     * <p>⚠️ Two threads upserting the same key can both find nothing and both insert. The unique
     * constraint on {@code (BUCKET, TYPE, REC_KEY)} is the backstop and the loser gets a constraint
     * violation rather than a duplicate row. For the batch pipeline this does not arise - a key
     * belongs to exactly one batch - but it is a real difference from an atomic upsert.
     */
    @Transactional
    public int upsert(String bucket, List<MetaRecordParams> records, long gen, long now) {
        int count = 0;
        for (MetaRecordParams p : records) {
            p.checkIntegrity();
            MetaStorageRecord row = metaStorageStubRepository.findByNaturalKey(bucket, p.type, p.recKey);
            if (row == null) {
                row = new MetaStorageRecord();
                row.bucket = bucket;
                row.type = p.type;
                row.recKey = p.recKey;
            }
            row.body = MetaRecordParamsUtils.BASE_JSON_UTILS.toString(p);
            row.gen = gen;
            row.updatedAt = now;
            metaStorageStubRepository.save(row);
            count++;
        }
        log.info("01.945.020 upsert bucket={}, records={}, rows={}, gen={}", bucket, records.size(), count, gen);
        return count;
    }
}
