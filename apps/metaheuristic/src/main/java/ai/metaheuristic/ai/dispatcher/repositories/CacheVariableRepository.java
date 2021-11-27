/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.beans.CacheVariable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Blob;
import java.util.List;

/**
 * @author Serge
 * Date: 10/27/2020
 * Time: 7:09 PM
 */
@Repository
@Profile("dispatcher")
public interface CacheVariableRepository extends CrudRepository<CacheVariable, Long> {

    @Override
    @Modifying
    @Query(value="delete from CacheVariable t where t.id=:id")
    void deleteById(Long id);

    @Transactional(propagation = Propagation.MANDATORY)
    void deleteByCacheProcessId(Long cacheProcessId);

    @Query(value="select b.data from CacheVariable b where b.id=:id")
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    Blob getDataAsStreamById(Long id);

    @Query(value="select b.id, b.variableName, b.nullified from CacheVariable b where b.cacheProcessId=:cacheProcessId")
    @Transactional(readOnly = true, propagation=Propagation.NOT_SUPPORTED)
    List<Object[]> getVarsByCacheProcessId(Long cacheProcessId);

}
