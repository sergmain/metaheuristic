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

import ai.metaheuristic.ai.launchpad.beans.Company;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Serge
 * Date: 10/27/2019
 * Time: 7:13 PM
 */
@Repository
@Transactional
@Profile("launchpad")
public interface CompanyRepository extends CrudRepository<Company, Long> {

    @Query(value="select a from Company a where a.uniqueId=:uniqueId")
    Company findByUniqueIdForUpdate(Long uniqueId);

    @Transactional(readOnly = true)
    @Query(value="select a from Company a where a.uniqueId=:uniqueId")
    Company findByUniqueId(Long uniqueId);

    @Transactional(readOnly = true)
    @Query(value="select a from Company a order by a.uniqueId")
    Page<Company> findAll(Pageable pageable);

    @Query(value="select max(c.uniqueId) from Company c")
    Long getMaxUniqueIdValue();

    @Transactional(readOnly = true)
    @Query(value="select c.uniqueId from Company c")
    List<Long> findAllUniqueIds();
}
