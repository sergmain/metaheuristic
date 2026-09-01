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

import ai.metaheuristic.ai.dispatcher.beans.MetaStorageSynthetic;
import ai.metaheuristic.ai.dispatcher.repositories.MetaStorageSyntheticRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Non-transactional orchestrator for the meta storage, per SPRING-TX-RULES.md.
 *
 * <p>Reads context, decides, then calls {@link MetaStorageTxService} with the decision as
 * small-scoped parameters. All the plain public reads live here rather than on the tx service, per
 * the two-way rule.
 *
 * <p>❗ MH never parses a body. {@code select} returns the stored strings; {@code upsert} stores the
 * strings it is given. The encoding and the structure of a body belong to whoever owns the data.
 *
 * <p>Error code prefix: {@code 01.941.} (unique to this class).
 *
 * @author Serge
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class MetaStorageSyntheticService {

    /**
     * IN-clause chunk size. Every engine has a parameter-count ceiling and they differ, so chunking
     * keeps a caller passing a whole key list at once degrading instead of failing.
     */
    public static final int MAX_KEYS_PER_QUERY = 500;

    private final MetaStorageSyntheticRepository metaStorageSyntheticRepository;
    private final MetaStorageSyntheticTxService metaStorageSyntheticTxService;

    /**
     * Read records of one type.
     *
     * <p>{@code recKeys} null or empty means every record of that type - the selection step that
     * feeds a batch splitter. Non-empty means exactly those records - the per-batch payload fetch.
     */
    public List<MetaStorageData.Record> select(Long companyId, String type, @Nullable List<String> recKeys) {
        if (recKeys==null || recKeys.isEmpty()) {
            return toRecords(metaStorageSyntheticRepository.findAllByCompanyIdAndType(companyId, type));
        }
        final List<MetaStorageData.Record> result = new ArrayList<>(recKeys.size());
        for (int from = 0; from < recKeys.size(); from += MAX_KEYS_PER_QUERY) {
            final List<String> chunk = recKeys.subList(from, Math.min(from + MAX_KEYS_PER_QUERY, recKeys.size()));
            result.addAll(toRecords(metaStorageSyntheticRepository.findAllByCompanyIdAndTypeAndRecKeys(companyId, type, chunk)));
        }
        return result;
    }

    /**
     * Insert-or-update on the natural key.
     *
     * <p>❗ The existence lookup happens HERE, outside any transaction, and the resolved row id is
     * handed to the tx service. Per SPRING-TX-RULES.md §1 a {@code @Transactional} method must not
     * perform the existence check its caller could have done - purity outranks avoiding the extra
     * query.
     */
    public int upsert(Long companyId, List<MetaStorageData.Record> records) {
        if (records.isEmpty()) {
            return 0;
        }
        final List<MetaStorageData.ResolvedWrite> writes = new ArrayList<>(records.size());
        for (MetaStorageData.Record r : records) {
            final MetaStorageSynthetic existing = metaStorageSyntheticRepository.findByNaturalKey(companyId, r.type(), r.recKey());
            writes.add(new MetaStorageData.ResolvedWrite(existing==null ? null : existing.id, r));
        }
        return metaStorageSyntheticTxService.upsert(companyId, writes, nextGeneration(companyId), System.currentTimeMillis());
    }

    /** Key list only, for the selection step feeding a batch splitter. Bodies stay unread. */
    public List<String> listKeys(Long companyId, String type) {
        return metaStorageSyntheticRepository.findRecKeysByCompanyIdAndType(companyId, type);
    }

    /** The types this company has ever written. The store enumerates itself - no registry needed. */
    public List<String> listTypes(Long companyId) {
        return metaStorageSyntheticRepository.findDistinctTypes(companyId);
    }

    /** Current generation for one (companyId, type). Zero when the type has never been written. */
    public long generation(Long companyId, String type) {
        final Long max = metaStorageSyntheticRepository.findMaxGen(companyId, type);
        return max==null ? 0L : max;
    }

    public long nextGeneration(Long companyId) {
        final Long max = metaStorageSyntheticRepository.findMaxGenByCompanyId(companyId);
        return (max==null ? 0L : max) + 1L;
    }

    private static List<MetaStorageData.Record> toRecords(List<MetaStorageSynthetic> rows) {
        final List<MetaStorageData.Record> result = new ArrayList<>(rows.size());
        for (MetaStorageSynthetic row : rows) {
            if (row.body==null) {
                throw new IllegalStateException(
                        "01.941.020 BODY is null, which the NOT NULL constraint should have prevented, recKey: " + row.recKey);
            }
            result.add(new MetaStorageData.Record(row.type, row.recKey, row.body));
        }
        return result;
    }
}
