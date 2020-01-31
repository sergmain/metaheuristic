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

import ai.metaheuristic.ai.launchpad.beans.GlobalBinaryData;
import ai.metaheuristic.ai.launchpad.binary_data.SimpleVariableAndStorageUrl;
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
import java.util.List;

/**
 * @author Serge
 * Date: 1/30/2020
 * Time: 6:17 PM
 */
@Repository
@Profile("launchpad")
public interface GlobalBinaryDataRepository extends CrudRepository<GlobalBinaryData, Long> {

    @Query(value="select new ai.metaheuristic.ai.launchpad.binary_data.SimpleVariableAndStorageUrl(" +
            "b.id, b.variable, b.params, b.filename ) " +
            "from GlobalBinaryData b where b.variable in :vars")
    List<SimpleVariableAndStorageUrl> getIdAndStorageUrlInVars(List<String> vars);

    List<GlobalBinaryData> findAllByVariable(String variable);

    @Query(value="select b.filename from GlobalBinaryData b where b.variable=:var")
    List<String> findFilenamesByVar(String var);

    @Transactional(readOnly = true)
    @Query(value="select b.data from GlobalBinaryData b where b.id=:id")
    Blob getDataAsStreamById(Long id);

    @Transactional
    @Query(value="select b from GlobalBinaryData b where b.id=:id")
    GlobalBinaryData findByIdForUpdate(Long id);

    @NonNull
    @Transactional(readOnly = true)
    Page<GlobalBinaryData> findAll(@NonNull Pageable pageable);

    @Transactional
    void deleteByVariable(String variable);

    @Query(value="select new ai.metaheuristic.ai.launchpad.launchpad_resource.SimpleVariable(" +
            "b.id, b.version, b.variable, b.uploadTs, b.filename, b.params ) " +
            "from GlobalBinaryData b " +
            "order by b.uploadTs desc ")
    Slice<SimpleVariable> getAllAsSimpleResources(Pageable pageable);


    @Query(value="select b.id from GlobalBinaryData b")
    List<Long> getAllIds();
}
