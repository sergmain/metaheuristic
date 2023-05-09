/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.mhbp.repositories;

import ai.metaheuristic.ai.mhbp.beans.Kb;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 4/15/2023
 * Time: 2:06 PM
 */
@Repository
@Profile("dispatcher")
public interface KbRepository extends CrudRepository<Kb, Long> {

    @Transactional(readOnly = true)
    @Query(value= "select a from Kb a where a.companyId=:companyUniqueId")
    Page<Kb> findAllByCompanyUniqueId(Pageable pageable, Long companyUniqueId);

    @Transactional(readOnly = true)
    @Query(value= "select a from Kb a where a.companyId=:companyUniqueId")
    List<Kb> findAllByCompanyUniqueId(Long companyUniqueId);

    @Transactional(readOnly = true)
    @Query(value= "select a.id from Kb a where a.companyId=:companyUniqueId")
    List<Long> findAllIdsByCompanyUniqueId(Long companyUniqueId);
}