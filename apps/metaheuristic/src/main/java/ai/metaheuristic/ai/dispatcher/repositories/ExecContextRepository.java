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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Profile("dispatcher")
//@Transactional
public interface ExecContextRepository extends CrudRepository<ExecContextImpl, Long> {

    @Override
    @Modifying
    @Query(value="delete from ExecContextImpl t where t.id=:id")
    void deleteById(Long id);

    @Query(nativeQuery = true, value =
            "select distinct d.ID from mh_exec_context d where d.SOURCE_CODE_ID not in (select z.id from mh_source_code z)")
    List<Long> findAllIdsForOrphanExecContexts();


    @Nullable
    @Query(value="select e from ExecContextImpl e where e.id=:id")
////    @Transactional(readOnly = true)
    ExecContextImpl findByIdForUpdate(@NonNull Long id);

    @Query(value="select w.id, w.state from ExecContextImpl w ")
//    @Transactional(readOnly = true)
    List<Object[]> findAllExecStates();

    @Query(value="select w.id from ExecContextImpl w")
//    @Transactional(readOnly = true)
    List<Long> findAllIds();

    // ai.metaheuristic.api.EnumsApi.ExecContextState
    // STARTED(3),         // started
    @Query(value="select w.id from ExecContextImpl w where w.state=3")
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    List<Long> findAllStartedIds();

    @Query(value="select e.id from ExecContextImpl e where e.state=:execState order by e.createdOn asc ")
//    @Transactional(readOnly = true)
    List<Long> findByStateOrderByCreatedOnAsc(int execState);

//    @Transactional(readOnly = true)
    List<ExecContextImpl> findByState(int execState);

//    @Transactional(readOnly = true)
    List<Long> findIdByState(int execState);

    @Query(value="select e.id from ExecContextImpl e where e.state=:execState")
    List<Long> findIdsByExecState(int execState);

/*
    ERROR(-2),          // some error in configuration
    UNKNOWN(-1),        // unknown state
    NONE(0),            // just created execContext
    PRODUCING(1),       // producing was just started
    NOT_USED_ANYMORE(2),        // former 'PRODUCED' status
    STARTED(3),         // started
    STOPPED(4),         // stopped

*/
    @Query(value="select count(e) from SourceCodeImpl sc, ExecContextImpl e where e.state in (1, 3, 4) and sc.uid=:sourceCodeUid and sc.id=e.sourceCodeId")
    int countInProgress(String sourceCodeUid);

    @Query(value="select e.id from ExecContextImpl e where e.sourceCodeId=:sourceCodeId")
//    @Transactional(readOnly = true)
    List<Long> findIdsBySourceCodeId(Long sourceCodeId);

//    @Transactional(readOnly = true)
    @Query(value="select new ai.metaheuristic.api.data.exec_context.ExecContextsListItem(" +
            "b.id, b.createdOn, b.valid, b.completedOn, b.state ) " +
            "from ExecContextImpl b " +
            "where b.sourceCodeId=:sourceCodeId " +
            "order by b.createdOn desc ")
    Slice<ExecContextsListItem> findBySourceCodeIdOrderByCreatedOnDesc(Pageable pageable, Long sourceCodeId);

}

