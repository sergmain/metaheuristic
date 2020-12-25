/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.beans.CacheProcess;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Serge
 * Date: 10/7/2020
 * Time: 9:29 PM
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
@Profile("dispatcher")
public interface CacheProcessRepository extends CrudRepository<CacheProcess, Long> {

    @Override
    @Modifying
    @Query(value="delete from CacheProcess t where t.id=:id")
    void deleteById(Long id);

    @Nullable
    @Query(value="select c from CacheProcess c where c.keySha256Length=:keySha256Length")
    CacheProcess findByKeySha256Length(String keySha256Length);


}
