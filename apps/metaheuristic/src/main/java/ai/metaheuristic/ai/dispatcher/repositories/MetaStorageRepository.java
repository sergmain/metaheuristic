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
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageData;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
public interface MetaStorageRepository extends JpaRepository<MetaStorage, Long> {

    @Nullable
    @Transactional(readOnly = true)
    @Query("SELECT m FROM MetaStorage m WHERE m.companyId=:companyId AND m.type=:type AND m.recKey=:recKey")
    MetaStorage findByNaturalKey(@Param("companyId") Long companyId, @Param("type") String type,
                                 @Param("recKey") String recKey);

    /** Every record of one type, ordered by recKey so a run is reproducible. */
    @Transactional(readOnly = true)
    @Query("SELECT m FROM MetaStorage m WHERE m.companyId=:companyId AND m.type=:type ORDER BY m.recKey")
    List<MetaStorage> findAllByCompanyIdAndType(@Param("companyId") Long companyId, @Param("type") String type);

    /** A named subset - the per-batch payload fetch, once a splitter has handed a task its slice. */
    @Transactional(readOnly = true)
    @Query("SELECT m FROM MetaStorage m WHERE m.companyId=:companyId AND m.type=:type AND m.recKey IN :recKeys ORDER BY m.recKey")
    List<MetaStorage> findAllByCompanyIdAndTypeAndRecKeys(@Param("companyId") Long companyId, @Param("type") String type,
                                                          @Param("recKeys") List<String> recKeys);

    /** Key list only - the selection step feeding a batch splitter. Bodies stay unread. */
    @Transactional(readOnly = true)
    @Query("SELECT m.recKey FROM MetaStorage m WHERE m.companyId=:companyId AND m.type=:type ORDER BY m.recKey")
    List<String> findRecKeysByCompanyIdAndType(@Param("companyId") Long companyId, @Param("type") String type);

    /**
     * One page of key list, for the browse screen.
     *
     * <p>❗ The count query is spelled out rather than derived. Spring Data builds one by rewriting the
     * select clause, and a rewrite of a scalar projection carrying ORDER BY is exactly the shape that
     * goes wrong quietly - a wrong total is not a failure, it is a Next button that stops working.
     *
     * <p>Ordering by recKey rather than by id: a page boundary has to fall in the same place on every
     * request, and a consumer deletes keys as it drains, so insertion order is not stable here.
     */
    @Transactional(readOnly = true)
    @Query(value="SELECT m.recKey FROM MetaStorage m WHERE m.companyId=:companyId AND m.type=:type ORDER BY m.recKey",
           countQuery="SELECT COUNT(m) FROM MetaStorage m WHERE m.companyId=:companyId AND m.type=:type")
    Page<String> findRecKeyPageByCompanyIdAndType(Pageable pageable, @Param("companyId") Long companyId, @Param("type") String type);

    @Nullable
    @Transactional(readOnly = true)
    @Query("SELECT max(m.gen) FROM MetaStorage m WHERE m.companyId=:companyId AND m.type=:type")
    Long findMaxGen(@Param("companyId") Long companyId, @Param("type") String type);

    @Nullable
    @Transactional(readOnly = true)
    @Query("SELECT max(m.gen) FROM MetaStorage m WHERE m.companyId=:companyId")
    Long findMaxGenByCompanyId(@Param("companyId") Long companyId);

    @Transactional(readOnly = true)
    @Query("SELECT DISTINCT m.type FROM MetaStorage m WHERE m.companyId=:companyId ORDER BY m.type")
    List<String> findDistinctTypes(@Param("companyId") Long companyId);

    /**
     * Every meta table in this store, across every company - the management-company enumeration.
     *
     * <p>❗ Deliberately NOT {@link #findDistinctTypes}. That one answers "what does THIS company
     * hold" and returns bare names, which is all a single-company caller can use. A caller entitled
     * to the whole installation needs the partition alongside the name, or two companies holding a
     * table of the same name are indistinguishable in the result.
     *
     * <p>{@code GROUP BY} rather than {@code SELECT DISTINCT}: with a constructor expression the two
     * mean the same thing here, and only one of them states plainly which columns the grouping is
     * over.
     */
    @Transactional(readOnly = true)
    @Query("SELECT new ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageData$TypeRef(m.companyId, m.type) " +
           "FROM MetaStorage m GROUP BY m.companyId, m.type ORDER BY m.companyId, m.type")
    List<MetaStorageData.TypeRef> findAllTypeRefs();
}
