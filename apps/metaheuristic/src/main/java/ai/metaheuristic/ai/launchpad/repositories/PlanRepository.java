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

import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.api.launchpad.Plan;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional
@Profile("launchpad")
public interface PlanRepository extends JpaRepository<PlanImpl, Long> {

    @Query(value="select p from PlanImpl p where p.id=:id")
    Plan findByIdForUpdate(Long id);

    @Transactional(readOnly = true)
    Page<PlanImpl> findAll(Pageable pageable);

    @Transactional(readOnly = true)
    @Query(value="select p from PlanImpl p")
    List<Plan> findAllAsPlan();

    @Transactional(readOnly = true)
    Slice<Plan> findAllByOrderByIdDesc(Pageable pageable);

    @Transactional(readOnly = true)
    List<Plan> findAllByOrderByIdDesc();

    @Transactional(readOnly = true)
    PlanImpl findByCode(String code);
}


