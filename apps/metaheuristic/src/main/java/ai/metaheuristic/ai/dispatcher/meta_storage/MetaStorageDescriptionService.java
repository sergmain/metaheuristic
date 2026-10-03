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

/**
 * Setting the description of one meta table from the browse screen.
 *
 * <p>Non-transactional orchestrator, per SPRING-TX-RULES.md: it reads and decides, and the one write
 * goes through {@link MetaStorageRegistryTxService#upsert} - the single write path into
 * MH_META_STORAGE_REGISTRY, shared with the MCP tool and the internal Function, so a description set
 * here means what a description set anywhere else means.
 *
 * <p>❗ Refused for a table with no records in the chosen store. A table exists only by virtue of its
 * records, so a descriptor registered for a name with none would describe nothing - the exact
 * leftover that dropping a table goes out of its way to remove.
 *
 * <p>❗ Refused when the existing descriptor's PARAMS do not parse. The edit replaces one field and
 * keeps the rest, and an unreadable payload has no "rest" that can be kept: storing the new
 * description would silently throw away whatever else was in there.
 *
 * <p>Error code prefix: {@code 01.955.} (unique to this class).
 *
 * @author Serge
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class MetaStorageDescriptionService {

    private final MetaStorageService metaStorageService;
    private final MetaStorageSyntheticService metaStorageSyntheticService;
    private final MetaStorageRegistryRepository metaStorageRegistryRepository;
    private final MetaStorageRegistryTxService metaStorageRegistryTxService;

    /**
     * @param description the description as stored; null when refused
     * @param created     true when the table had no descriptor and one was registered
     * @param error       null on success, otherwise why nothing was stored
     */
    public record DescriptionResult(@Nullable String description, boolean created, @Nullable String error) {
        public boolean ok() {
            return error==null;
        }
    }

    /**
     * @param production true edits the descriptor of the table in MH_META_STORAGE, false the one in
     *                   MH_META_STORAGE_SYNTHETIC. The caller has already resolved {@code companyId}
     *                   to what the principal may touch.
     */
    public DescriptionResult updateDescription(Long companyId, String metaTable, boolean production, @Nullable String description) {
        final String descriptionError = MetaStorageDescriptionUtils.checkDescription(description);
        if (descriptionError!=null || description==null) {
            return refused(companyId, metaTable, production, descriptionError==null ? "01.955.010 no description" : descriptionError);
        }

        final long records = (production
                ? metaStorageService.listKeys(companyId, metaTable, PageRequest.of(0, 1))
                : metaStorageSyntheticService.listKeys(companyId, metaTable, PageRequest.of(0, 1))).getTotalElements();
        if (records==0) {
            return refused(companyId, metaTable, production,
                    "01.955.020 Meta table '" + metaTable + "' holds no records in " + MetaStorageCloneUtils.storeName(production)
                            + ". A description is registered only for a table that exists.");
        }

        final MetaStorageRegistry existing =
                metaStorageRegistryRepository.findByCompanyIdAndMetaTableAndProd(companyId, metaTable, production);
        MetaStorageRegistryParams existingParams = null;
        if (existing!=null) {
            try {
                existingParams = existing.getMetaStorageRegistryParams();
            }
            catch (Throwable th) {
                return refused(companyId, metaTable, production,
                        "01.955.040 The registry descriptor of meta table '" + metaTable + "' in " + MetaStorageCloneUtils.storeName(production)
                                + " can't be parsed, so its description can't be changed without losing the rest of it. "
                                + "Correct the descriptor with mh_upsert_meta_storage_registry. Error: " + th.getMessage());
            }
        }

        final MetaStorageRegistryParams params = MetaStorageDescriptionUtils.withDescription(existingParams, description);
        metaStorageRegistryTxService.upsert(companyId, metaTable, production, params);

        log.info("01.955.060 description of meta table '{}' of company #{} in {} {}",
                metaTable, companyId, MetaStorageCloneUtils.storeName(production), existing==null ? "registered" : "updated");
        return new DescriptionResult(params.desc, existing==null, null);
    }

    private static DescriptionResult refused(Long companyId, String metaTable, boolean production, String error) {
        log.warn("01.955.080 description of meta table '{}' of company #{} in {} not changed: {}",
                metaTable, companyId, MetaStorageCloneUtils.storeName(production), error);
        return new DescriptionResult(null, false, error);
    }
}
