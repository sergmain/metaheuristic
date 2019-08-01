/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

import ai.metaheuristic.ai.launchpad.beans.TaskImpl;
import ai.metaheuristic.api.launchpad.Task;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

@Repository
@Transactional
@Profile("launchpad")
public interface TaskRepository extends CrudRepository<TaskImpl, Long> {

    @Transactional
    @Query(value="select t from TaskImpl t where t.id=:id")
    TaskImpl findByIdForUpdate(Long id);

    @Transactional(readOnly = true)
    @Query(value="select t.id, t.metrics from TaskImpl t where t.id in :ids ")
    List<Object[]> findMetricsByIds(List<Long> ids);

    @Transactional(readOnly = true)
    Page<TaskImpl> findAll(Pageable pageable);

    @Transactional(readOnly = true)
    List<Task> findAllByWorkbookId(Long workbookId);

    @Transactional
    @Query(value="select t from TaskImpl t where t.workbookId=:workbookId")
    Stream<Task> findAllByWorkbookIdAsStream(Long workbookId);

    @Transactional
    @Query(value="select t.id, t.workbookId from TaskImpl t")
    Stream<Object[]> findAllAsTaskSimple(Pageable pageable);

    @Transactional(readOnly = true)
    List<Task> findByStationIdAndResultReceivedIsFalse(Long stationId);

    @Transactional(readOnly = true)
    @Query(value="select t.id, t.assignedOn from TaskImpl t " +
            "where t.stationId=:stationId and t.resultReceived=false")
    List<Object[]> findAllByStationIdAndResultReceivedIsFalse(Long stationId);

    @Transactional(readOnly = true)
    @Query(value="select t.id, t.assignedOn from TaskImpl t " +
            "where t.stationId=:stationId and t.resultReceived=false and t.isCompleted=false")
    List<Object[]> findAllByStationIdAndResultReceivedIsFalseAndCompletedIsFalse(Long stationId);

    @Transactional(readOnly = true)
    @Query(value="select t.id, t.execState from TaskImpl t where t.workbookId=:workbookId")
    List<Object[]> findAllExecStateByWorkbookId(Long workbookId);

    @Transactional
    void deleteByWorkbookId(Long workbookId);

    @Transactional
    @Query(value="select t.id, t.params from TaskImpl t where t.workbookId=:workbookId")
    Stream<Object[]> findByWorkbookId(Long workbookId);

    @Transactional
    @Query("SELECT t FROM TaskImpl t where t.stationId is null and t.workbookId=:workbookId and t.id in :ids ")
    List<Task> findForAssigning(Long workbookId, List<Long> ids);

    @Transactional(readOnly = true)
    @Query("SELECT t.id FROM TaskImpl t where t.stationId=:stationId and t.isCompleted=false")
    List<Long> findAnyActiveForStationId(Pageable limit, Long stationId);

    @Transactional(readOnly = true)
    @Query("SELECT t FROM TaskImpl t where t.stationId=:stationId and t.resultReceived=false and " +
            " t.execState =:execState and (:mills - result_resource_scheduled_on > 15000) ")
    List<Task> findForMissingResultResources(Long stationId, long mills, int execState);

    @Transactional(readOnly = true)
    // execState>1 --> 1==Enums.TaskExecState.IN_PROGRESS
    @Query("SELECT t FROM TaskImpl t where t.id in :ids and t.execState > 1 ")
    List<Task> findByIsCompletedIsTrueAndIds(List<Long> ids);

    @Transactional(readOnly = true)
    @Query("SELECT t FROM TaskImpl t where t.id in :ids order by t.id asc ")
    List<TaskImpl> findTasksByIds(List<Long> ids);


    @SuppressWarnings("SqlRedundantOrderingDirection")
    @Transactional(readOnly = true)
    @Query(nativeQuery = true, value = "select z.* "+
            "from ( "+
            "           SELECT count(*) count, t.TASK_ORDER "+
            "           FROM MH_TASK t  \n"+
            "           where t.WORKBOOK_ID =:workbookId "+
            "           group by t.TASK_ORDER "+
            "     ) z "+
            "order by z.TASK_ORDER asc")
    List<Object[]> getCountPerOrder(Long workbookId);

}

