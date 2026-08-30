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

import ai.metaheuristic.ai.dispatcher.beans.MetaStorage;
import ai.metaheuristic.ai.dispatcher.repositories.MetaStorageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Owns the write transaction for the meta storage, per SPRING-TX-RULES.md.
 *
 * <p>❗ This class holds ONLY {@code @Transactional} write methods - never a plain public read.
 * Every input arrives already resolved: {@link MetaStorageData.ResolvedWrite} carries the existing
 * row id (or null) that {@link MetaStorageService} looked up BEFORE the transaction was opened, and
 * {@code gen} / {@code now} are computed there too. Nothing here re-verifies context, looks anything
 * up, or decides anything - transactional purity takes precedence over avoiding the duplicated
 * lookup in the orchestrator.
 *
 * <p>Error code prefix: {@code 01.940.} (unique to this class).
 *
 * @author Serge
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class MetaStorageTxService {

    private final MetaStorageRepository metaStorageRepository;

    /**
     * Insert-or-update the already-resolved records.
     *
     * <p>⚠️ Two callers upserting the same natural key concurrently can both arrive with
     * {@code existingId == null} and both insert; the UNIQUE constraint on
     * {@code (COMPANY_ID, TYPE, REC_KEY)} is the backstop and the loser gets a constraint violation
     * rather than a duplicate row. For a batch pipeline this does not arise - a key belongs to
     * exactly one batch.
     */
    @Transactional
    public int upsert(Long companyId, List<MetaStorageData.ResolvedWrite> writes, long gen, long now) {
        int count = 0;
        for (MetaStorageData.ResolvedWrite w : writes) {
            final MetaStorage row;
            if (w.existingId()==null) {
                row = new MetaStorage();
                row.companyId = companyId;
                row.type = w.record().type();
                row.recKey = w.record().recKey();
            }
            else {
                row = metaStorageRepository.findById(w.existingId()).orElseThrow(
                        () -> new IllegalStateException("01.940.020 record disappeared between resolution and write, id: " + w.existingId()));
            }
            row.body = w.record().body();
            row.gen = gen;
            row.updatedAt = now;
            metaStorageRepository.save(row);
            count++;
        }
        log.info("01.940.040 upsert companyId: {}, records: {}, gen: {}", companyId, count, gen);
        return count;
    }

    @Transactional
    public void deleteByIds(List<Long> ids) {
        metaStorageRepository.deleteAllById(ids);
        log.info("01.940.060 deleted {} records", ids.size());
    }
}
