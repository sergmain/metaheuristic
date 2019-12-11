/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.batch;

import ai.metaheuristic.ai.launchpad.batch.data.BatchExecStatus;
import ai.metaheuristic.ai.launchpad.beans.Batch;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Repository
@Profile("launchpad")
@Transactional(propagation = Propagation.REQUIRES_NEW)
public interface BatchRepository extends JpaRepository<Batch, Long> {

    @Query(value="select b from Batch b where b.id=:id and b.companyId=:companyId")
    Batch findByIdForUpdate(Long id, Long companyId);

    @Transactional(readOnly = true)
    @Query("select b.id from Batch b where b.companyId=:companyId order by b.createdOn desc")
    Page<Long> findAllByOrderByCreatedOnDesc(Pageable pageable, Long companyId);

    @Transactional(readOnly = true)
    @Query("select b.id from Batch b where b.companyId=:companyId and b.deleted=false order by b.createdOn desc")
    Page<Long> findAllExcludeDeletedByOrderByCreatedOnDesc(Pageable pageable, Long companyId);

    @Transactional(readOnly = true)
    @Query(value="select new ai.metaheuristic.ai.launchpad.batch.data.BatchExecStatus(b.id, b.execState) " +
            "from Batch b where b.companyId=:companyId")
    List<BatchExecStatus> getBatchExecStatuses(Long companyId);
}
