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
package ai.metaheuristic.ai.dispatcher.repositories;

import ai.metaheuristic.ai.dispatcher.beans.MetaStorageRegistry;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reads of MH_META_STORAGE_REGISTRY. Addressed by (META_TABLE, PROD), which is what the unique index
 * declares and what a caller actually knows - a row id here is an internal allocation nothing refers to.
 *
 * @author Serge
 */
@Repository
@Transactional
@Profile("dispatcher")
public interface MetaStorageRegistryRepository extends JpaRepository<MetaStorageRegistry, Long> {

    @Nullable
    @Transactional(readOnly = true)
    @Query("SELECT m FROM MetaStorageRegistry m WHERE m.metaTable=:metaTable AND m.prod=:prod")
    MetaStorageRegistry findByMetaTableAndProd(@Param("metaTable") String metaTable, @Param("prod") boolean prod);

    @Transactional(readOnly = true)
    @Query("SELECT m FROM MetaStorageRegistry m WHERE m.companyId=:companyId AND m.prod=:prod ORDER BY m.metaTable")
    List<MetaStorageRegistry> findAllByCompanyIdAndProd(@Param("companyId") Long companyId, @Param("prod") boolean prod);

    @Transactional(readOnly = true)
    @Query("SELECT m FROM MetaStorageRegistry m WHERE m.companyId=:companyId ORDER BY m.prod, m.metaTable")
    List<MetaStorageRegistry> findAllByCompanyId(@Param("companyId") Long companyId);
}