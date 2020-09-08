/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Blob;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 15:41
 */
@Repository
@Profile("dispatcher")
public interface VariableRepository extends CrudRepository<Variable, Long> {

    @Transactional(readOnly = true)
    @Query("SELECT v.taskContextId FROM Variable v where v.execContextId=:execContextId and v.name in (:names)")
    Set<String> findTaskContextIdsByExecContextIdAndVariableNames(Long execContextId, Set<String> names);

    @Transactional(readOnly = true)
    @Query(nativeQuery = true, value =
            "select d.id from mh_variable d where d.EXEC_CONTEXT_ID is not null and d.EXEC_CONTEXT_ID not in (select z.id from mh_exec_context z)")
    List<Long> findAllOrphanExecContextData();

    @Transactional(readOnly = true)
    @Query(value="select new ai.metaheuristic.ai.dispatcher.variable.SimpleVariable(v.id, v.name, v.params, v.filename, v.inited, v.nullified, v.taskContextId) " +
            "from Variable v where v.name in :vars and v.execContextId=:execContextId")
    List<SimpleVariable> findByExecContextIdAndNames(Long execContextId, Collection<String> vars);

    @Transactional(readOnly = true)
    @Query(value="select new ai.metaheuristic.ai.dispatcher.variable.SimpleVariable(v.id, v.name, v.params, v.filename, v.inited, v.nullified, v.taskContextId) " +
            "from Variable v where v.execContextId=:execContextId")
    List<SimpleVariable> findByExecContextId(Long execContextId);

    @Transactional(readOnly = true)
    @Query(value="select new ai.metaheuristic.ai.dispatcher.variable.SimpleVariable(v.id, v.name, v.params, v.filename, v.inited, v.nullified, v.taskContextId) " +
            "from Variable v where v.execContextId=:execContextId and v.name in (:names)")
    List<SimpleVariable> getIdAndStorageUrlInVarsForExecContext(Long execContextId, String[] names);

    @Nullable
    @Transactional(readOnly = true)
    @Query(value="select new ai.metaheuristic.ai.dispatcher.variable.SimpleVariable(v.id, v.name, v.params, v.filename, v.inited, v.nullified, v.taskContextId) " +
            "from Variable v where v.name=:name and v.taskContextId=:taskContextId and v.execContextId=:execContextId")
    SimpleVariable findByNameAndTaskContextIdAndExecContextId(String name, String taskContextId, Long execContextId);

    @Transactional(readOnly = true)
    @Query(value="select v.id from Variable v where v.name=:name and v.execContextId=:execContextId")
    List<Long> findIdByNameAndExecContextId(String name, Long execContextId);

    @Query(value="select new ai.metaheuristic.ai.dispatcher.variable.SimpleVariable(" +
            "b.id, b.name, b.params, b.filename, b.inited, b.nullified, b.taskContextId) " +
            "from Variable b where b.name in :vars")
    List<SimpleVariable> getIdAndStorageUrlInVars(Set<String> vars);

    @Query(value="select b.filename from Variable b where b.name=:variable and b.execContextId=:execContextId ")
    List<String> findFilenameByVariableAndExecContextId(Long execContextId, String variable);

    @NonNull
    @Transactional(readOnly = true)
    Optional<Variable> findById(@NonNull Long id);

    @Nullable
    @Transactional(readOnly = true)
    @Query(value="select b.data from Variable b where b.id=:id")
    Blob getDataAsStreamById(Long id);

    @Nullable
    @Transactional
    @Query(value="select b from Variable b where b.id=:id")
    Variable findByIdForUpdate(Long id);

    @NonNull
    @Transactional(readOnly = true)
    Page<Variable> findAll(@NonNull Pageable pageable);

    @Transactional
    void deleteById(@NonNull Long id);

    @Transactional
    void deleteByExecContextId(Long execContextId);

    @Transactional
    void deleteByName(String variable);
}
