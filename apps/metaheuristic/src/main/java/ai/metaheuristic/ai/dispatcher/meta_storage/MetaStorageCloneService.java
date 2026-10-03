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

import ai.metaheuristic.ai.dispatcher.beans.MetaStorageRegistry;
import ai.metaheuristic.ai.dispatcher.repositories.MetaStorageRegistryRepository;
import ai.metaheuristic.api.data.meta_storage.MetaStorageRegistryParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Cloning one whole meta table: every record under one (companyId, type) in one store, written under
 * a new name in the same store - a clone inside a domain - or under any name in the other store - a
 * copy between domains. The table's registry descriptor, when it has one, goes with it.
 *
 * <p>The one implementation behind both the REST endpoint and the MCP tools. A table cloned by a human
 * from the browse screen and one cloned by an agent have to come out the same, and the way to
 * guarantee that is for there to be one place that decides what cloning means.
 *
 * <p>Order of work, and why:
 * <ol>
 *   <li>names - nothing is read for a request that can never succeed;</li>
 *   <li>the state of both stores - source populated, target absent, no leftover descriptor;</li>
 *   <li>the source descriptor is read and PARSED - a descriptor that will not parse refuses the clone
 *       here, before any record is written, rather than after the records are already in;</li>
 *   <li>the records, in rounds, one transaction per round;</li>
 *   <li>the descriptor, last - so it never describes records that are not there yet.</li>
 * </ol>
 *
 * <p>❗ Records are written as INSERTS, through the tx services with no existing id. The existence
 * check was made for the whole target table in step 2, so there is nothing per record to resolve, and
 * the orchestrators' own upsert would be wrong here: a record that appeared in the target after the
 * check would be silently OVERWRITTEN by an upsert, while an insert collides with the UNIQUE natural
 * key and stops the copy, which is then reported as incomplete.
 *
 * <p>Non-transactional orchestrator, per SPRING-TX-RULES.md: every round opens its own transaction in
 * the tx services, so a table of any size is copied in many small transactions.
 *
 * <p>Error code prefix: {@code 01.953.} (unique to this class).
 *
 * @author Serge
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class MetaStorageCloneService {

    private final MetaStorageService metaStorageService;
    private final MetaStorageSyntheticService metaStorageSyntheticService;
    private final MetaStorageTxService metaStorageTxService;
    private final MetaStorageSyntheticTxService metaStorageSyntheticTxService;
    private final MetaStorageRegistryRepository metaStorageRegistryRepository;
    private final MetaStorageRegistryTxService metaStorageRegistryTxService;

    /**
     * @param copied           records written into the target. On an incomplete copy this is how many
     *                         are there - they are not rolled back
     * @param descriptorCopied whether the source had a descriptor and the target now has a copy of it
     * @param error            null on success; otherwise why the clone was refused or where it stopped
     */
    public record CloneResult(int copied, boolean descriptorCopied, @Nullable String error) {
        public boolean ok() {
            return error==null;
        }
    }

    /**
     * @param sourceProduction true reads MH_META_STORAGE, false MH_META_STORAGE_SYNTHETIC
     * @param targetProduction true writes MH_META_STORAGE, false MH_META_STORAGE_SYNTHETIC. Equal to
     *                         {@code sourceProduction} for a clone inside a domain.
     *                         The caller has already resolved {@code companyId} to what the principal may touch.
     */
    public CloneResult clone(Long companyId, String sourceTable, boolean sourceProduction,
                             String targetTable, boolean targetProduction) {

        final String nameError = MetaStorageCloneUtils.checkNames(sourceTable, sourceProduction, targetTable, targetProduction);
        if (nameError!=null) {
            return refused(companyId, sourceTable, sourceProduction, targetTable, targetProduction, nameError);
        }

        final MetaStorageCloneUtils.ClonePreconditions preconditions = new MetaStorageCloneUtils.ClonePreconditions(
                countRecords(companyId, sourceTable, sourceProduction),
                countRecords(companyId, targetTable, targetProduction),
                metaStorageRegistryRepository.findByCompanyIdAndMetaTableAndProd(companyId, targetTable, targetProduction)!=null);
        final String stateError = MetaStorageCloneUtils.checkState(sourceTable, sourceProduction, targetTable, targetProduction, preconditions);
        if (stateError!=null) {
            return refused(companyId, sourceTable, sourceProduction, targetTable, targetProduction, stateError);
        }

        final MetaStorageRegistry sourceDescriptor =
                metaStorageRegistryRepository.findByCompanyIdAndMetaTableAndProd(companyId, sourceTable, sourceProduction);
        MetaStorageRegistryParams descriptorParams = null;
        if (sourceDescriptor!=null) {
            try {
                descriptorParams = sourceDescriptor.getMetaStorageRegistryParams();
            }
            catch (Throwable th) {
                return refused(companyId, sourceTable, sourceProduction, targetTable, targetProduction,
                        "01.953.020 The registry descriptor of meta table '" + sourceTable + "' in "
                                + MetaStorageCloneUtils.storeName(sourceProduction) + " can't be parsed, nothing was cloned. "
                                + "Correct or drop the descriptor first. Error: " + th.getMessage());
            }
        }

        final List<String> recKeys = sourceProduction
                ? metaStorageService.listKeys(companyId, sourceTable)
                : metaStorageSyntheticService.listKeys(companyId, sourceTable);

        final MetaStorageCloneUtils.CopyOutcome outcome = MetaStorageCloneUtils.copyRecords(
                recKeys, targetTable, MetaStorageCloneUtils.CLONE_CHUNK_SIZE,
                keys -> sourceProduction
                        ? metaStorageService.select(companyId, sourceTable, keys)
                        : metaStorageSyntheticService.select(companyId, sourceTable, keys),
                records -> insert(companyId, records, targetProduction));

        if (outcome.failure()!=null) {
            final String error = "01.953.060 Cloning meta table '" + sourceTable + "' from " + MetaStorageCloneUtils.storeName(sourceProduction)
                    + " into '" + targetTable + "' in " + MetaStorageCloneUtils.storeName(targetProduction) + " stopped after "
                    + outcome.copied() + " record(s). The target table is INCOMPLETE and has no descriptor - drop it before retrying. "
                    + "Error: " + outcome.failure();
            log.error(error);
            return new CloneResult(outcome.copied(), false, error);
        }

        if (descriptorParams!=null) {
            metaStorageRegistryTxService.upsert(companyId, targetTable, targetProduction, descriptorParams);
        }

        log.info("01.953.040 meta table '{}' of company #{} cloned from {} into '{}' in {}: {} record(s), descriptor {}",
                sourceTable, companyId, MetaStorageCloneUtils.storeName(sourceProduction),
                targetTable, MetaStorageCloneUtils.storeName(targetProduction),
                outcome.copied(), descriptorParams!=null ? "copied" : "absent");
        return new CloneResult(outcome.copied(), descriptorParams!=null, null);
    }

    /**
     * How many records carry this name. The count query of the paged key list - one COUNT and one short
     * page - rather than the whole key list, which for a large table would be read only to be measured.
     */
    private long countRecords(Long companyId, String type, boolean production) {
        return (production
                ? metaStorageService.listKeys(companyId, type, PageRequest.of(0, 1))
                : metaStorageSyntheticService.listKeys(companyId, type, PageRequest.of(0, 1))).getTotalElements();
    }

    /** One round, written as inserts in one transaction of the target store. */
    private int insert(Long companyId, List<MetaStorageData.Record> records, boolean production) {
        final List<MetaStorageData.ResolvedWrite> writes = new ArrayList<>(records.size());
        for (MetaStorageData.Record r : records) {
            writes.add(new MetaStorageData.ResolvedWrite(null, r));
        }
        final long now = System.currentTimeMillis();
        return production
                ? metaStorageTxService.upsert(companyId, writes, metaStorageService.nextGeneration(companyId), now)
                : metaStorageSyntheticTxService.upsert(companyId, writes, metaStorageSyntheticService.nextGeneration(companyId), now);
    }

    private static CloneResult refused(Long companyId, String sourceTable, boolean sourceProduction,
                                       String targetTable, boolean targetProduction, String error) {
        log.warn("01.953.080 clone of meta table '{}' of company #{} from {} into '{}' in {} refused: {}",
                sourceTable, companyId, MetaStorageCloneUtils.storeName(sourceProduction),
                targetTable, MetaStorageCloneUtils.storeName(targetProduction), error);
        return new CloneResult(0, false, error);
    }
}
