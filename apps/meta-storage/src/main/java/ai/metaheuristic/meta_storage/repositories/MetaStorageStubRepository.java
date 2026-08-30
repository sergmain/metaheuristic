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

package ai.metaheuristic.meta_storage.repositories;

import ai.metaheuristic.meta_storage.beans.MetaStorageStub;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Spring Data repository over the stub table on the MAIN datasource.
 *
 * <p>It resolves the single auto-configured {@code DataSource}. Nothing here reaches the SQLite
 * meta storage - that is {@code MetaStorageSpi}'s job and it does not participate in these
 * transactions.
 *
 * @author Serge
 */
@Repository
@Transactional
public interface MetaStorageStubRepository extends JpaRepository<MetaStorageStub, Long> {

    @Nullable
    @Transactional(readOnly = true)
    @Query("SELECT s FROM MetaStorageStub s WHERE s.companyId=:companyId AND s.code=:code")
    MetaStorageStub findByCompanyIdAndCode(@Param("companyId") Long companyId, @Param("code") String code);

    @Transactional(readOnly = true)
    @Query("SELECT s.code FROM MetaStorageStub s WHERE s.companyId=:companyId ORDER BY s.code")
    List<String> findCodesByCompanyId(@Param("companyId") Long companyId);
}
