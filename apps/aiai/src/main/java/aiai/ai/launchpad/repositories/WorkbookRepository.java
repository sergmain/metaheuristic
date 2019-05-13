/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.repositories;

import aiai.ai.launchpad.beans.WorkbookImpl;
import metaheuristic.api.v1.launchpad.Workbook;
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

    Slice<Workbook> findByPlanId(Pageable pageable, long planId);

    Slice<Workbook> findByPlanIdOrderByCreatedOnDesc(Pageable pageable, long planId);

    List<Workbook> findByPlanId(long planId);
}

