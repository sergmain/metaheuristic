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

import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Blob;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 15:41
 */
@Repository
@Profile("dispatcher")
public interface VariableRepository extends CrudRepository<Variable, Long> {

    @Override
    @Modifying
    @Query(value="delete from Variable t where t.id=:id")
    void deleteById(Long id);

    @Query(value="select distinct v.execContextId from Variable v")
    List<Long> getAllExecContextIds();

    void deleteAllByIdIn(List<Long> ids);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query(value="select new ai.metaheuristic.ai.dispatcher.variable.SimpleVariable(v.id, v.name, v.params, v.filename, v.inited, v.nullified, v.taskContextId) " +
            "from Variable v where v.name in :vars and v.execContextId=:execContextId")
    List<SimpleVariable> findByExecContextIdAndNames(Long execContextId, Collection<String> vars);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query(value="select new ai.metaheuristic.ai.dispatcher.variable.SimpleVariable(v.id, v.name, v.params, v.filename, v.inited, v.nullified, v.taskContextId) " +
            "from Variable v where v.execContextId=:execContextId and v.name in (:names)")
    List<SimpleVariable> getIdAndStorageUrlInVarsForExecContext(Long execContextId, String[] names);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Nullable
    @Query(value="select new ai.metaheuristic.ai.dispatcher.variable.SimpleVariable(v.id, v.name, v.params, v.filename, v.inited, v.nullified, v.taskContextId) " +
            "from Variable v where v.name=:name and v.taskContextId=:taskContextId and v.execContextId=:execContextId")
    SimpleVariable findByNameAndTaskContextIdAndExecContextId(String name, String taskContextId, Long execContextId);

    @Nullable
    @Query(value="select new ai.metaheuristic.ai.dispatcher.variable.SimpleVariable(v.id, v.name, v.params, v.filename, v.inited, v.nullified, v.taskContextId) " +
            "from Variable v where v.id=:variableId")
    SimpleVariable findByIdAsSimple(Long variableId);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query(value="select new ai.metaheuristic.ai.dispatcher.variable.SimpleVariable(" +
            "b.id, b.name, b.params, b.filename, b.inited, b.nullified, b.taskContextId) " +
            "from Variable b where b.name in :vars")
    List<SimpleVariable> getIdAndStorageUrlInVars(Set<String> vars);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query(value="select b.filename from Variable b where b.name=:variable and b.execContextId=:execContextId ")
    List<String> findFilenameByVariableAndExecContextId(Long execContextId, String variable);

    @Query(value="select v.id from Variable v where v.execContextId=:execContextId")
    List<Long> findAllByExecContextId(Pageable pageable, Long execContextId);

    @Nullable
    @Query(value="select b.data from Variable b where b.id=:id")
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    Blob getDataAsStreamById(Long id);

    @Modifying
    @Query(value="delete from Variable v where v.execContextId=:execContextId")
    void deleteByExecContextId(Long execContextId);

    void deleteByName(String variable);

}
