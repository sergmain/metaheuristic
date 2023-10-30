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

package ai.metaheuristic.ai.dispatcher.repositories;

import ai.metaheuristic.ai.dispatcher.beans.FunctionData;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Blob;
import java.util.List;

/**
 * @author Serge
 * Date: 1/23/2020
 * Time: 9:31 PM
 */
@Repository
//@Transactional
@Profile("dispatcher")
public interface FunctionDataRepository extends CrudRepository<FunctionData, Long> {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    @Query(value="select b.functionCode from FunctionData b ")
    List<String> findAllFunctionCodes();

    @Nullable
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    @Query(value="select b.data from FunctionData b where b.id=:id")
    Blob getDataAsStreamById(Long id);

    @Override
    @Modifying
    @Query(value="delete from FunctionData t where t.id=:id")
    void deleteById(Long id);

    @Nullable
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    @Query(value="select b.data from FunctionData b where b.functionCode=:functionCode")
    Blob getDataAsStreamByCode(String functionCode);

//    @Transactional(readOnly = true)
    @Nullable
    @Query(value="select b from FunctionData b where b.functionCode=:functionCode")
    FunctionData findByCodeForUpdate(String functionCode);

    @Nullable
    @Query(value="select b.id from FunctionData b where b.functionCode=:functionCode")
    Long findIdByCode(String functionCode);

    @Modifying
    @Transactional
    void deleteByFunctionCode(String functionCode);
}
