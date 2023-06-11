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

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Repository
@Profile("dispatcher")
public interface TaskRepository extends CrudRepository<TaskImpl, Long> {

//    @NonNull
//    @Override
//    @Transactional(readOnly = true)
//    Optional<TaskImpl> findById(Long id);

    @Nullable
    @Transactional(readOnly = true)
    @Query("SELECT t FROM TaskImpl t where t.id=:id")
    TaskImpl findByIdReadOnly(Long id);

    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    @Query("SELECT t FROM TaskImpl t where t.id in :ids")
    Stream<TaskImpl> findByIds(List<Long> ids);

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    @Query("SELECT t.id FROM TaskImpl t where t.execContextId=:execContextId and t.execState=7")
    List<Long> findTaskForErrorWithRecoveryState(Long execContextId);

    @Query(value="select t.execState, count(*) as count_records from TaskImpl t, ExecContextImpl e " +
            "where (e.rootExecContextId=:execContextId or e.id=:execContextId) and e.id = t.execContextId " +
            "group by t.execState ")
    List<Object[]> getTaskExecStates(Long execContextId);

    @Query(value="select t.id, t.execState, t.completedOn from TaskImpl t, ExecContextImpl e where (e.rootExecContextId=:execContextId or e.id=:execContextId) and e.id = t.execContextId ")
    List<Object[]> getSimpleTaskInfos(Long execContextId);

    @Override
    @Modifying
    @Query(value="delete from TaskImpl t where t.id=:id")
    void deleteById(Long id);

//    ERROR(-2),          // some error in configuration
//    FINISHED(5),        // finished
    @Query(value= """
            select t.id
            from TaskImpl t, ExecContextImpl e
            where t.execContextId=e.id and t.execState=1 and (e.state=5 or e.state=-2)""")
    List<Long> getUnfinishedTaskForFinishedExecContext();

    @Query(value="select distinct t.execContextId from TaskImpl t")
    List<Long> getAllExecContextIds();

    @Nullable
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    @Query(value="select t.execContextId from TaskImpl t where t.id=:taskId")
    Long getExecContextId(Long taskId);

    @Query(value="select t.id from TaskImpl t where t.execContextId=:execContextId")
//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    List<Long> findAllTaskIdsByExecContextId(Long execContextId);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query(value="select t.id, t.assignedOn, t.execContextId from TaskImpl t " +
            "where t.coreId=:processorId and t.resultReceived=0 and t.completed=0")
    List<Object[]> findAllByProcessorIdAndResultReceivedIsFalseAndCompletedIsFalse(Long processorId);

    // IN_PROGRESS(1)
    @Query(value="select t.id, t.execState, t.execContextId from TaskImpl t where t.coreId=:coreId and t.execState=1")
    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    List<Object[]> findExecStateByCoreId(Long coreId);

    // IN_PROGRESS(1)
    @Query(value="select t.coreId from TaskImpl t where t.execState=1")
    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    Set<Long> findCoreIdsWithInProgress();

    @Query(value="select t.id from TaskImpl t where t.coreId=:coreId")
    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    List<Long> findTaskIdsForCoreId(Long coreId);

    @Query(value="select t.id, t.execState, t.updatedOn from TaskImpl t where t.execContextId=:execContextId")
    List<Object[]> findExecStateByExecContextId(Long execContextId);

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    @Query("SELECT t.id FROM TaskImpl t where t.coreId is null and t.execContextId=:execContextId and (t.execState=0 or t.execState=6) and t.id in :ids")
    List<Long> findForAssigning(Long execContextId, List<Long> ids);

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    @Query("SELECT t FROM TaskImpl t where t.coreId=:processorId and t.resultReceived=0 and " +
            " t.execState =:execState and (:mills - t.resultResourceScheduledOn > 15000) ")
    List<TaskImpl> findForMissingResultVariables(Long processorId, long mills, int execState);

    @Query(value="select v.id from TaskImpl v where v.execContextId=:execContextId")
    List<Long> findAllByExecContextId(Pageable pageable, Long execContextId);

    @Modifying
    @Query(value="delete from TaskImpl t where t.id in (:ids)")
    void deleteByIds(List<Long> ids);

    @Modifying
    @Query("update TaskImpl t set t.accessByProcessorOn = :mills where t.id = :taskId")
    void updateAccessByProcessorOn(Long taskId, long mills);

    @Modifying
    @Transactional
    @Query("update TaskImpl t set t.execState=5 where t.id in (:ids)")
    void updateTaskAsFinished(List<Long> ids);
}

