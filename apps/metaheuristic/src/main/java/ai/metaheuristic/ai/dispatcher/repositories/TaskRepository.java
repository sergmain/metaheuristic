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

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.TaskProgress;
import ai.metaheuristic.api.dispatcher.Task;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

@Repository
//@Transactional
@Profile("dispatcher")
public interface TaskRepository extends CrudRepository<TaskImpl, Long> {

//    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    @Query(value="select t from TaskImpl t where t.execContextId=:execContextId")
    Stream<TaskImpl> findAllByExecContextIdAsStream(Long execContextId);

    @Nullable
    @Query(value="select t from TaskImpl t where t.id=:id")
//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    TaskImpl findByIdForUpdate(Long id);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    Page<TaskImpl> findAll(Pageable pageable);

    @Query(value="select t.id from TaskImpl t where t.execContextId=:execContextId")
//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    List<Long> findAllTaskIdsByExecContextId(Long execContextId);

    @Query(value="select t.id, t.execContextId from TaskImpl t")
//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    List<Object[]> findAllAsTaskSimple(Pageable pageable);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    List<Task> findByProcessorIdAndResultReceivedIsFalse(Long processorId);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query(value="select t.id, t.assignedOn from TaskImpl t " +
            "where t.processorId=:processorId and t.resultReceived=false")
    List<Object[]> findAllByProcessorIdAndResultReceivedIsFalse(Long processorId);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query(value="select t.id, t.assignedOn, t.execContextId from TaskImpl t " +
            "where t.processorId=:processorId and t.resultReceived=false and t.isCompleted=false")
    List<Object[]> findAllByProcessorIdAndResultReceivedIsFalseAndCompletedIsFalse(Long processorId);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query(value="select t.id, t.execState, t.updatedOn, t.params from TaskImpl t where t.execContextId=:execContextId")
    List<Object[]> findAllExecStateByExecContextId(Long execContextId);

    void deleteByExecContextId(Long execContextId);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query(value="select t.id, t.params from TaskImpl t where t.execContextId=:execContextId")
    List<Object[]> findByExecContextId(Long execContextId);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query(value="select t from TaskImpl t where t.execContextId=:execContextId")
    List<TaskImpl> findByExecContextIdAsList(Long execContextId);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query("SELECT t FROM TaskImpl t where t.processorId is null and t.execContextId=:execContextId and t.id in :ids ")
    List<TaskImpl> findForAssigning(Long execContextId, List<Long> ids);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query("SELECT t.id FROM TaskImpl t where t.processorId=:processorId and t.isCompleted=false")
    List<Long> findAnyActiveForProcessorId(Pageable limit, Long processorId);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query("SELECT t.id FROM TaskImpl t where t.processorId=:processorId and t.isCompleted=false")
    List<Long> findActiveForProcessorId(Long processorId);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query("SELECT t FROM TaskImpl t where t.execContextId=:execContextId and t.execState = :execState ")
    List<TaskImpl> findTasksByExecState(Long execContextId, int execState);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query("SELECT t FROM TaskImpl t where t.processorId=:processorId and t.resultReceived=false and " +
            " t.execState =:execState and (:mills - t.resultResourceScheduledOn > 15000) ")
    List<Task> findForMissingResultResources(Long processorId, long mills, int execState);

    // execState>1 --> 1==Enums.TaskExecState.IN_PROGRESS
//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query("SELECT t FROM TaskImpl t where t.id in :ids and t.execState > 1 ")
    List<Task> findByIsCompletedIsTrueAndIds(List<Long> ids);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query("SELECT t FROM TaskImpl t where t.id in :ids order by t.id asc ")
    List<TaskImpl> findTasksByIds(List<Long> ids);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query(value="select new ai.metaheuristic.ai.dispatcher.data.TaskProgress(" +
            "t.execContextId, count(*), t.execState, t.isCompleted, t.resultReceived ) " +
            "from TaskImpl t where t.execContextId=:execContextId " +
            "group by t.execContextId, t.execState, t.isCompleted, t.resultReceived "
    )
    List<TaskProgress> getTaskProgress(Long execContextId);

    @Query(nativeQuery = true, value =
            "select distinct d.EXEC_CONTEXT_ID from mh_task d where d.EXEC_CONTEXT_ID not in (select z.id from mh_exec_context z)")
    List<Long> findAllExecContextIdsForOrphanTasks();

//    @Query("DELETE FROM TaskImpl t where t.id in :ids")
    void deleteAllByIdIn(List<Long> ids);

    @Query(value="select v.id from TaskImpl v where v.execContextId=:execContextId")
    List<Long> findAllByExecContextId(Pageable pageable, Long execContextId);
}

