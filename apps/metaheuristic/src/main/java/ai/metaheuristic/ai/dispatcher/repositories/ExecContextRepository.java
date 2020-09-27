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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.api.data.exec_context.ExecContextsListItem;
import lombok.NonNull;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Profile("dispatcher")
@Transactional
public interface ExecContextRepository extends CrudRepository<ExecContextImpl, Long> {

    @Nullable
    @Query(value="select e from ExecContextImpl e where e.id=:id")
    @Transactional(readOnly = true)
    ExecContextImpl findByIdForUpdate(@NonNull Long id);

    @Query(value="select w.id, w.state from ExecContextImpl w ")
    @Transactional(readOnly = true)
    List<Object[]> findAllExecStates();

    @Query(value="select w.id from ExecContextImpl w")
    @Transactional(readOnly = true)
    List<Long> findAllIds();

    @Query(value="select e.id from ExecContextImpl e where e.state=:execState order by e.createdOn asc ")
    @Transactional(readOnly = true)
    List<Long> findByStateOrderByCreatedOnAsc(int execState);

    @Transactional(readOnly = true)
    List<ExecContextImpl> findByState(int execState);

    @Query(value="select e.id from ExecContextImpl e where e.state=:execState")
    @Transactional(readOnly = true)
    List<Long> findIdsByExecState(int execState);

    @Query(value="select e.id from ExecContextImpl e where e.sourceCodeId=:sourceCodeId")
    @Transactional(readOnly = true)
    List<Long> findIdsBySourceCodeId(Long sourceCodeId);

    @Transactional(readOnly = true)
    @Query(value="select new ai.metaheuristic.api.data.exec_context.ExecContextsListItem(" +
            "b.id, b.createdOn, b.valid, b.completedOn, b.state ) " +
            "from ExecContextImpl b " +
            "where b.sourceCodeId=:sourceCodeId " +
            "order by b.createdOn desc ")
    Slice<ExecContextsListItem> findBySourceCodeIdOrderByCreatedOnDesc(Pageable pageable, Long sourceCodeId);

}

