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

package ai.metaheuristic.ai.launchpad.repositories;

import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.api.launchpad.Workbook;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Repository
@Transactional
@Profile("launchpad")
public interface WorkbookRepository extends CrudRepository<WorkbookImpl, Long> {

    @Query(value="select e from WorkbookImpl e where e.id=:id")
    WorkbookImpl findByIdForUpdate(Long id);

    @Transactional(readOnly = true)
    @Query(value="select w.id, w.execState from WorkbookImpl w ")
    List<Object[]> findAllExecStates();

    @Transactional(readOnly = true)
    @Query(value="select w.id from WorkbookImpl w")
    List<Long> findAllIds();

    @Transactional(readOnly = true)
    Slice<Workbook> findAllByOrderByExecStateDescCompletedOnDesc(Pageable pageable);

    @Transactional(readOnly = true)
    @Query(value="select e.id from WorkbookImpl e where e.execState=:execState order by e.createdOn asc ")
    List<Long> findByExecStateOrderByCreatedOnAsc(int execState);

    @Transactional
    List<WorkbookImpl> findByExecState(int execState);

    @Transactional(readOnly = true)
    @Query(value="select e.id from WorkbookImpl e where e.execState=:execState")
    List<Long> findIdsByExecState(int execState);

    Slice<Workbook> findByPlanId(Pageable pageable, Long planId);

    @Transactional(readOnly = true)
    Slice<Workbook> findByPlanIdOrderByCreatedOnDesc(Pageable pageable, Long planId);

    @Transactional(readOnly = true)
    Workbook findFirstByPlanId(Long planId);

    @Transactional(readOnly = true)
    @Query(value="select w from BatchWorkbook b, WorkbookImpl w where b.batchId=:batchId and b.workbookId=w.id")
    List<Workbook> findWorkbookByBatchId(long batchId);

    @Transactional(readOnly = true)
    @Query(value="select w.execState from BatchWorkbook b, WorkbookImpl w where b.batchId=:batchId and b.workbookId=w.id")
    List<Integer> findWorkbookExecStateByBatchId(long batchId);
}

