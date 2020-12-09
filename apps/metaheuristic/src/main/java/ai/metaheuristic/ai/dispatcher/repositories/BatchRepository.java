/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

import ai.metaheuristic.ai.dispatcher.batch.data.BatchExecStatus;
import ai.metaheuristic.ai.dispatcher.beans.Batch;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Profile("dispatcher")
public interface BatchRepository extends CrudRepository<Batch, Long> {

    @Override
    @Modifying
    @Query(value="delete from Batch t where t.id=:id")
    void deleteById(Long id);

    @Transactional(readOnly = true)
    @Nullable
    @Query(value="select b.execContextId from Batch b where b.id=:batchId")
    Long getExecContextId(Long batchId);

    @Query(value="select b from Batch b where b.id=:id and b.companyId=:companyUniqueId")
    Batch findByIdForUpdate(Long id, Long companyUniqueId);

    @Nullable
    @Query(value="select b from Batch b where b.id=:id")
    Batch findByIdForUpdate(Long id);

//    @Transactional(readOnly = true)
    @Query("select b.id from Batch b where b.companyId=:companyUniqueId order by b.createdOn desc")
    Page<Long> findAllByOrderByCreatedOnDesc(Pageable pageable, Long companyUniqueId);

//    @Transactional(readOnly = true)
    @Query("select b.id from Batch b where b.companyId=:companyUniqueId and b.accountId=:accountId order by b.createdOn desc")
    Page<Long> findAllForAccountByOrderByCreatedOnDesc(Pageable pageable, Long companyUniqueId, Long accountId);

//    @Transactional(readOnly = true)
    @Query("select b.id from Batch b where b.companyId=:companyUniqueId and b.deleted=false order by b.createdOn desc")
    Page<Long> findAllExcludeDeletedByOrderByCreatedOnDesc(Pageable pageable, Long companyUniqueId);

//    @Transactional(readOnly = true)
    @Query("select b.id from Batch b where b.companyId=:companyUniqueId and b.deleted=false and b.accountId=:accountId order by b.createdOn desc")
    Page<Long> findAllForAccountExcludeDeletedByOrderByCreatedOnDesc(Pageable pageable, Long companyUniqueId, Long accountId);

//    @Transactional(readOnly = true)
    @Query(value="select new ai.metaheuristic.ai.dispatcher.batch.data.BatchExecStatus(b.id, b.execState) " +
            "from Batch b where b.companyId=:companyUniqueId")
    List<BatchExecStatus> getBatchExecStatuses(Long companyUniqueId);

//    @Transactional(readOnly = true)
    @Query(value="select b.id from Batch b where b.execState=3")
    List<Long> findAllUnfinishedAsId();

}
