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
 * Reads of MH_META_STORAGE_REGISTRY. Addressed by (COMPANY_ID, META_TABLE, PROD), which is what the
 * unique index declares and what a caller actually knows - a row id here is an internal allocation
 * nothing refers to.
 *
 * @author Serge
 */
@Repository
@Transactional
@Profile("dispatcher")
public interface MetaStorageRegistryRepository extends JpaRepository<MetaStorageRegistry, Long> {

    /**
     * The one descriptor addressed by the natural key.
     *
     * <p>❗ COMPANY_ID is part of that key because the described table is per-company: MH_META_STORAGE
     * is unique on {@code (COMPANY_ID, TYPE, REC_KEY)}, so two companies holding a type of the same
     * name hold two tables over two unrelated sets of records, and each needs its own description.
     * Addressing a descriptor without a company would hand one company the other's answer.
     */
    @Nullable
    @Transactional(readOnly = true)
    @Query("SELECT m FROM MetaStorageRegistry m WHERE m.companyId=:companyId AND m.metaTable=:metaTable AND m.prod=:prod")
    MetaStorageRegistry findByCompanyIdAndMetaTableAndProd(@Param("companyId") Long companyId,
                                                           @Param("metaTable") String metaTable, @Param("prod") boolean prod);

    @Transactional(readOnly = true)
    @Query("SELECT m FROM MetaStorageRegistry m WHERE m.companyId=:companyId AND m.prod=:prod ORDER BY m.metaTable")
    List<MetaStorageRegistry> findAllByCompanyIdAndProd(@Param("companyId") Long companyId, @Param("prod") boolean prod);

    @Transactional(readOnly = true)
    @Query("SELECT m FROM MetaStorageRegistry m WHERE m.companyId=:companyId ORDER BY m.prod, m.metaTable")
    List<MetaStorageRegistry> findAllByCompanyId(@Param("companyId") Long companyId);

    /**
     * Every descriptor for one store, across every company, in one read.
     *
     * <p>❗ Deliberately unfiltered by company: this feeds the management-company index screen, which
     * lists every company's meta tables on one tab and would otherwise issue one lookup per table.
     * A caller scoped to a single company uses {@link #findAllByCompanyIdAndProd} instead - the
     * scoping decision belongs to the caller that knows the entitlement, not to this query.
     */
    @Transactional(readOnly = true)
    @Query("SELECT m FROM MetaStorageRegistry m WHERE m.prod=:prod ORDER BY m.metaTable")
    List<MetaStorageRegistry> findAllByProd(@Param("prod") boolean prod);
}