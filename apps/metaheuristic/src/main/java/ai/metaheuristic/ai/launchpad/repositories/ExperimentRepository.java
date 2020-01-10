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

import ai.metaheuristic.ai.launchpad.beans.Experiment;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 15:41
 */
@Repository
@Transactional
@Profile("launchpad")
public interface ExperimentRepository extends JpaRepository<Experiment, Long> {

    @Query(value="select e from Experiment e where e.id=:id")
    Experiment findByIdForUpdate(Long id);

    @Transactional(readOnly = true)
    @Query(value="select e.id from Experiment e where e.workbookId is not null")
    List<Long> findAllIds();

    @Transactional(readOnly = true)
    Page<Experiment> findAll(Pageable pageable);

    @Transactional(readOnly = true)
    @Query(value="select e.id from Experiment e order by e.id desc")
    Slice<Long> findAllByOrderByIdDesc(Pageable pageable);

    @Transactional(readOnly = true)
    @Query(value="select e.id from Experiment e where e.workbookId=:workbookId")
    Long findIdByWorkbookId(long workbookId);

    @Transactional
    @Query(value="select e from Experiment e where e.workbookId=:workbookId")
    Experiment findByWorkbookIdForUpdate(Long workbookId);

    @Override
    @Transactional(readOnly = true)
    List<Experiment> findAll();

    @Transactional(readOnly = true)
    @Query(value="select e.id from Experiment e where e.code=:code")
    Long findIdByCode(String code);

    @Transactional(readOnly = true)
    @Query(value="select e from Experiment e where e.code=:code")
    Experiment findByCode(String code);
}
