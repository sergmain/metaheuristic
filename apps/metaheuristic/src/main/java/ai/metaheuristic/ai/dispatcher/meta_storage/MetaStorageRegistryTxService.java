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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single write path into MH_META_STORAGE_REGISTRY, shared by the MCP tool and by the internal
 * Function. Two callers, one implementation: a descriptor written from a pipeline and one written by
 * a human have to mean the same thing, and the way to guarantee that is for there to be only one
 * place that decides what writing means.
 *
 * @author Serge
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class MetaStorageRegistryTxService {

    private final MetaStorageRegistryRepository metaStorageRegistryRepository;

    /**
     * Register or correct the descriptor of one meta storage table, addressed by (metaTable, prod).
     *
     * <p>createdOn is stamped on first registration and preserved afterwards: an update is a correction
     * of the description, not a new registration, and moving it forward would erase the one field that
     * answers how old the table is.
     */
    @Transactional
    public MetaStorageRegistry upsert(Long companyId, String metaTable, boolean prod, MetaStorageRegistryParams params) {
        MetaStorageRegistry r = metaStorageRegistryRepository.findByMetaTableAndProd(metaTable, prod);
        if (r==null) {
            r = new MetaStorageRegistry();
            r.companyId = companyId;
            r.metaTable = metaTable;
            r.prod = prod;
            r.createdOn = System.currentTimeMillis();
        }
        r.updateParams(params);
        return metaStorageRegistryRepository.save(r);
    }

    /** Returns true when a descriptor existed and was removed. A missing one is not an error. */
    @Transactional
    public boolean deleteByMetaTableAndProd(String metaTable, boolean prod) {
        final MetaStorageRegistry r = metaStorageRegistryRepository.findByMetaTableAndProd(metaTable, prod);
        if (r==null) {
            return false;
        }
        metaStorageRegistryRepository.delete(r);
        return true;
    }
}