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

import ai.metaheuristic.ai.dispatcher.beans.GlobalVariable;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariableAndStorageUrl;
import ai.metaheuristic.ai.dispatcher.variable_global.SimpleGlobalVariable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Blob;
import java.util.List;

/**
 * @author Serge
 * Date: 1/30/2020
 * Time: 6:17 PM
 */
@Repository
@Profile("dispatcher")
public interface GlobalVariableRepository extends CrudRepository<GlobalVariable, Long> {

    @Nullable
    @Transactional(readOnly = true)
    @Query(value="select v from GlobalVariable v where v.name=:name")
    GlobalVariable findIdByName(String name);

    @Query(value="select new ai.metaheuristic.ai.dispatcher.variable.SimpleVariableAndStorageUrl(" +
            "b.id, b.name, b.params, b.filename ) " +
            "from GlobalVariable b where b.name in :vars")
    List<SimpleVariableAndStorageUrl> getIdAndStorageUrlInVars(List<String> vars);

    List<GlobalVariable> findAllByName(String name);

    @Query(value="select b.filename from GlobalVariable b where b.name=:var")
    List<String> findFilenamesByVar(String var);

    @Nullable
    @Transactional(readOnly = true)
    @Query(value="select b.data from GlobalVariable b where b.id=:id")
    Blob getDataAsStreamById(Long id);

    @Nullable
    @Transactional
    @Query(value="select b from GlobalVariable b where b.id=:id")
    GlobalVariable findByIdForUpdate(Long id);

    @NonNull
    @Transactional(readOnly = true)
    Page<GlobalVariable> findAll(@NonNull Pageable pageable);

    @Transactional
    void deleteByName(String variable);

    @Transactional(readOnly = true)
    @Query(value="select new ai.metaheuristic.ai.dispatcher.variable_global.SimpleGlobalVariable(" +
            "b.id, b.version, b.name, b.uploadTs, b.filename, b.params ) " +
            "from GlobalVariable b " +
            "order by b.uploadTs desc ")
    Slice<SimpleGlobalVariable> getAllAsSimpleGlobalVariable(Pageable pageable);

    @Nullable
    @Transactional(readOnly = true)
    @Query(value="select new ai.metaheuristic.ai.dispatcher.variable_global.SimpleGlobalVariable(" +
            "b.id, b.version, b.name, b.uploadTs, b.filename, b.params ) " +
            "from Variable b " +
            "where b.id=:id")
    SimpleGlobalVariable getByIdAsSimpleGlobalVariable(Long id);

    @Transactional(readOnly = true)
    @Query(value="select b.id from GlobalVariable b")
    List<Long> getAllIds();
}
