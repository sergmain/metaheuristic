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

package ai.metaheuristic.ai.launchpad.repositories;

import ai.metaheuristic.ai.launchpad.beans.Variable;
import ai.metaheuristic.ai.launchpad.variable.SimpleVariableAndStorageUrl;
import ai.metaheuristic.ai.launchpad.launchpad_resource.SimpleVariable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Blob;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 15:41
 */
@Repository
@Profile("launchpad")
public interface VariableRepository extends CrudRepository<Variable, Long> {

    @Transactional(readOnly = true)
    @Query(nativeQuery = true, value = "select d.id from mh_variable d where d.WORKBOOK_ID is not null and d.WORKBOOK_ID not in (select z.id from mh_exec_context z)")
    List<Long> findAllOrphanWorkbookData();

    @Query(value="select new ai.metaheuristic.ai.launchpad.variable.SimpleVariableAndStorageUrl(" +
            "b.id, b.name, b.params, b.filename ) " +
            "from Variable b where b.name in :vars and " +
            "b.workbookId=:workbookId")
    List<SimpleVariableAndStorageUrl> getIdAndStorageUrlInVarsForWorkbook(List<String> vars, Long workbookId);

    @Transactional(readOnly = true)
    @Query(value="select b.workbookId, b.filename from Variable b " +
            "where b.workbookId in :ids ")
    List<Object[]> getFilenamesForBatchIds(Collection<Long> ids);

    @Query(value="select new ai.metaheuristic.ai.launchpad.variable.SimpleVariableAndStorageUrl(" +
            "b.id, b.name, b.params, b.filename ) " +
            "from Variable b where b.name in :vars")
    List<SimpleVariableAndStorageUrl> getIdAndStorageUrlInVars(List<String> vars);

    List<Variable> findAllByName(String variableName);

    @Query(value="select b.filename from Variable b where b.name=:var")
    String findFilenameByVar(String var);

    @Query(value="select b.filename from Variable b where b.name=:variable and b.workbookId=:workbookId ")
    List<String> findFilenameByVariableAndWorkbookId(String variable, Long workbookId);

    @NonNull
    @Transactional(readOnly = true)
    Optional<Variable> findById(@NonNull Long id);

    @Transactional(readOnly = true)
    @Query(value="select b.data from Variable b where b.id=:id")
    Blob getDataAsStreamByCode(Long id);

    @Transactional
    @Query(value="select b from Variable b where b.id=:id")
    Variable findByIdForUpdate(Long id);

    @NonNull
    @Transactional(readOnly = true)
    Page<Variable> findAll(@NonNull Pageable pageable);

    @Transactional
    void deleteById(@NonNull Long id);

    @Transactional
    void deleteByWorkbookId(Long workbookId);

    @Transactional
    void deleteByName(String variable);

    @Transactional(readOnly = true)
    @Query(value="select new ai.metaheuristic.ai.launchpad.launchpad_resource.SimpleVariable(" +
            "b.id, b.version, b.name, b.uploadTs, b.filename, b.params ) " +
            "from Variable b " +
            "order by b.uploadTs desc ")
    Slice<SimpleVariable> getAllAsSimpleResources(Pageable pageable);

    @Transactional(readOnly = true)
    @Query(value="select b.id from Variable b")
    List<Long> getAllIds();
}
