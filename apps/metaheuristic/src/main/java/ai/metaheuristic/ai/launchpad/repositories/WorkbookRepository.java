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
import ai.metaheuristic.api.v1.launchpad.Workbook;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

@Repository
@Profile("launchpad")
@Transactional
public interface WorkbookRepository extends CrudRepository<WorkbookImpl, Long> {

    @Transactional(readOnly = true)
    @Query(value="select f from WorkbookImpl f")
    Stream<Workbook> findAllAsStream();

    @Transactional(readOnly = true)
    Slice<Workbook> findAllByOrderByExecStateDescCompletedOnDesc(Pageable pageable);


    List<Workbook> findByExecStateOrderByCreatedOnAsc(int execSate);

    List<WorkbookImpl> findByExecState(int execState);

    Slice<Workbook> findByPlanId(Pageable pageable, Long planId);

    Slice<Workbook> findByPlanIdOrderByCreatedOnDesc(Pageable pageable, Long planId);

    Workbook findFirstByPlanId(Long planId);

    @Transactional(readOnly = true)
    @Query(value="select w from BatchWorkbook b, WorkbookImpl w where b.batchId=:batchId and b.workbookId=w.id")
    List<Workbook> findWorkbookByBatchId(long batchId);
}

