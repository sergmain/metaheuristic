/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.api.dispatcher.SourceCode;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional
@Profile("dispatcher")
public interface SourceCodeRepository extends CrudRepository<SourceCodeImpl, Long> {

    @Nullable
    @Transactional(readOnly = true)
    @Query(value="select e from SourceCodeImpl e where e.id=:sourceCodeId")
    SourceCodeImpl findByIdNullable(Long sourceCodeId);

    @Nullable
    @Transactional(readOnly = true)
    @Query(value="select sc from SourceCodeImpl sc, ExecContextImpl e where e.id=:execContextId and e.sourceCodeId=sc.id")
    SourceCodeImpl findByIdExecContextId(Long execContextId);

    @Override
    @Modifying
    @Query(value="delete from SourceCodeImpl t where t.id=:id")
    void deleteById(Long id);

    @Transactional(readOnly = true)
    @Nullable
    @Query(value="select p from SourceCodeImpl p where p.id=:id and p.companyId=:companyUniqueId")
    SourceCodeImpl findByIdForUpdate(Long id, Long companyUniqueId);

    @Transactional(readOnly = true)
    @Query(value="select p from SourceCodeImpl p where p.companyId=:companyUniqueId")
    List<SourceCodeImpl> findAllAsSourceCode(Long companyUniqueId);

    @Transactional(readOnly = true)
    @Query(value="select p from SourceCodeImpl p where p.companyId=:companyUniqueId order by p.id desc ")
    List<SourceCode> findAllByOrderByIdDesc(Long companyUniqueId);

    @Transactional(readOnly = true)
    @Query(value="select p.id from SourceCodeImpl p where p.companyId=:companyUniqueId order by p.id desc ")
    List<Long> findAllIdsByOrderByIdDesc(Long companyUniqueId);

    @Transactional(readOnly = true)
    @Nullable
    @Query(value="select p from SourceCodeImpl p where p.uid=:uid and p.companyId=:companyUniqueId")
    SourceCodeImpl findByUidAndCompanyId(String uid, Long companyUniqueId);


    @Transactional(readOnly = true)
    // for Experiment, that's why we don't use companyId in this query
    @Query(value="select p.id from SourceCodeImpl p")
    List<Long> findAllAsIds();

    @Transactional(readOnly = true)
    @Query(value="select p.uid from SourceCodeImpl p")
    List<String> findAllSourceCodeUids();

    @Transactional(readOnly = true)
    @Nullable
    SourceCodeImpl findByUid(String uid);

    @Transactional(readOnly = true)
    @Nullable
    @Query(value="select p.id from SourceCodeImpl p where p.uid=:uid")
    Long findIdByUid(String uid);
}


