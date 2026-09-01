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

import ai.metaheuristic.ai.dispatcher.beans.MetaStorage;
import ai.metaheuristic.ai.dispatcher.beans.MetaStorageSynthetic;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The only place {@code @Transactional(readOnly = true)} belongs, per SPRING-TX-RULES.md.
 *
 * <p>Every query is JPQL, so the same operations run unchanged on H2, MySQL, MariaDB, PostgreSQL
 * and derby - Hibernate emits the dialect.
 *
 * @author Serge
 */
@Repository
@Profile("dispatcher")
public interface MetaStorageSyntheticRepository extends JpaRepository<MetaStorageSynthetic, Long> {

    @Nullable
    @Transactional(readOnly = true)
    @Query("SELECT m FROM MetaStorageSynthetic m WHERE m.companyId=:companyId AND m.type=:type AND m.recKey=:recKey")
    MetaStorageSynthetic findByNaturalKey(@Param("companyId") Long companyId, @Param("type") String type,
                                 @Param("recKey") String recKey);

    /** Every record of one type, ordered by recKey so a run is reproducible. */
    @Transactional(readOnly = true)
    @Query("SELECT m FROM MetaStorageSynthetic m WHERE m.companyId=:companyId AND m.type=:type ORDER BY m.recKey")
    List<MetaStorageSynthetic> findAllByCompanyIdAndType(@Param("companyId") Long companyId, @Param("type") String type);

    /** A named subset - the per-batch payload fetch, once a splitter has handed a task its slice. */
    @Transactional(readOnly = true)
    @Query("SELECT m FROM MetaStorageSynthetic m WHERE m.companyId=:companyId AND m.type=:type AND m.recKey IN :recKeys ORDER BY m.recKey")
    List<MetaStorageSynthetic> findAllByCompanyIdAndTypeAndRecKeys(@Param("companyId") Long companyId, @Param("type") String type,
                                                          @Param("recKeys") List<String> recKeys);

    /** Key list only - the selection step feeding a batch splitter. Bodies stay unread. */
    @Transactional(readOnly = true)
    @Query("SELECT m.recKey FROM MetaStorageSynthetic m WHERE m.companyId=:companyId AND m.type=:type ORDER BY m.recKey")
    List<String> findRecKeysByCompanyIdAndType(@Param("companyId") Long companyId, @Param("type") String type);

    @Nullable
    @Transactional(readOnly = true)
    @Query("SELECT max(m.gen) FROM MetaStorageSynthetic m WHERE m.companyId=:companyId AND m.type=:type")
    Long findMaxGen(@Param("companyId") Long companyId, @Param("type") String type);

    @Nullable
    @Transactional(readOnly = true)
    @Query("SELECT max(m.gen) FROM MetaStorageSynthetic m WHERE m.companyId=:companyId")
    Long findMaxGenByCompanyId(@Param("companyId") Long companyId);

    @Transactional(readOnly = true)
    @Query("SELECT DISTINCT m.type FROM MetaStorageSynthetic m WHERE m.companyId=:companyId ORDER BY m.type")
    List<String> findDistinctTypes(@Param("companyId") Long companyId);
}
