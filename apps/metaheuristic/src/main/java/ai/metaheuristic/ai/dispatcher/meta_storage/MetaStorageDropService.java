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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Dropping one whole meta table: every record under one (companyId, type) in one store, and that
 * table's registry descriptor with it.
 *
 * <p>❗ The descriptor goes in the same act. Its lifetime is the table's lifetime - a descriptor left
 * behind for a table that no longer exists is worse than none, because it is the one artifact a
 * reader has decided to trust. Scoped to this company AND this store: another company may hold a
 * table of the same name, and the other store's descriptor describes other records.
 *
 * <p>Records are removed one natural key at a time through the orchestrators' own delete - the path
 * {@code mh_drop_meta_storage_table} takes - so a key that vanished between listing and deleting is a
 * no-op rather than a failure, and a repeated drop reports 0 instead of erroring.
 *
 * <p>Non-transactional orchestrator, per SPRING-TX-RULES.md: each delete opens its own transaction in
 * the tx services.
 *
 * <p>Error code prefix: {@code 01.951.} (unique to this class).
 *
 * @author Serge
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class MetaStorageDropService {

    private final MetaStorageService metaStorageService;
    private final MetaStorageSyntheticService metaStorageSyntheticService;
    private final MetaStorageRegistryTxService metaStorageRegistryTxService;

    /**
     * @param deleted       records removed - 0 for a table already empty, which is not an error
     * @param hadDescriptor whether this store held a registry descriptor for the table
     */
    public record DropResult(int deleted, boolean hadDescriptor) {}

    /**
     * @param production true drops from MH_META_STORAGE, false from MH_META_STORAGE_SYNTHETIC. The
     *                   caller has already resolved {@code companyId} to what the principal may touch.
     */
    public DropResult drop(Long companyId, String metaTable, boolean production) {
        final List<String> recKeys = production
                ? metaStorageService.listKeys(companyId, metaTable)
                : metaStorageSyntheticService.listKeys(companyId, metaTable);

        int deleted = 0;
        for (String recKey : recKeys) {
            deleted += production
                    ? metaStorageService.deleteByNaturalKey(companyId, metaTable, recKey)
                    : metaStorageSyntheticService.deleteByNaturalKey(companyId, metaTable, recKey);
        }
        final boolean hadDescriptor = metaStorageRegistryTxService
                .deleteByCompanyIdAndMetaTableAndProd(companyId, metaTable, production);

        log.info("01.951.020 meta table '{}' of company #{} dropped from {}: {} record(s), descriptor {}",
                metaTable, companyId, production ? "MH_META_STORAGE" : "MH_META_STORAGE_SYNTHETIC",
                deleted, hadDescriptor ? "removed" : "absent");
        return new DropResult(deleted, hadDescriptor);
    }
}
