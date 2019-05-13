/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.repositories;

import aiai.ai.launchpad.beans.TaskImpl;
import metaheuristic.api.v1.data.TaskWIthType;
import metaheuristic.api.v1.launchpad.Task;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

@Component
@Profile("launchpad")
public interface TaskRepository extends CrudRepository<TaskImpl, Long> {

    @Transactional
    @Query(value="select t.id, t.metrics from TaskImpl t, ExperimentTaskFeature f " +
            "where t.id=f.taskId and f.featureId=:experimentFeatureId ")
    Stream<Object[]> findMetricsByExperimentFeatureId(long experimentFeatureId);

    @Transactional(readOnly = true)
    Slice<Task> findAll(Pageable pageable);

    List<Task> findAllByWorkbookId(long workbookId);

    @Transactional
    @Query(value="select t from TaskImpl t where t.workbookId=:workbookId")
    Stream<Task> findAllByWorkbookIdAsStream(long workbookId);

    @Query(value="select t.id, t.workbookId from TaskImpl t")
    Stream<Object[]> findAllAsTaskSimple(Pageable pageable);

    @Transactional(readOnly = true)
    List<Task> findByStationIdAndResultReceivedIsFalse(long stationId);

    @Transactional(readOnly = true)
    @Query(value="select t.id, t.assignedOn from TaskImpl t where t.stationId=:stationId and t.resultReceived=false")
    List<Object[]> findAllByStationIdAndResultReceivedIsFalse(long stationId);


    @Transactional
    void deleteByWorkbookId(long workbookId);

    @Transactional
    @Query(value="select t.id, t.params from TaskImpl t where t.workbookId=:workbookId")
    Stream<Object[]> findByWorkbookId(long workbookId);

    @Query("SELECT t FROM TaskImpl t where t.stationId is null and t.workbookId=:workbookId and t.order =:taskOrder")
    Slice<Task> findForAssigning(Pageable pageable, long workbookId, int taskOrder);

    @Query("SELECT max(t.order) as max_order FROM TaskImpl t where t.workbookId=:workbookId")
    Integer findMaxConcreteOrder(long workbookId);

    @Query("SELECT t FROM TaskImpl t where t.stationId is not null and t.workbookId=:workbookId and t.order =:taskOrder")
    List<Task> findWithConcreteOrder(long workbookId, int taskOrder);

    @Query("SELECT t FROM TaskImpl t where t.workbookId=:workbookId and t.order =:taskOrder")
    List<Task> findAnyWithConcreteOrder(long workbookId, int taskOrder);

    @Query("SELECT t.id FROM TaskImpl t where t.stationId is null and t.workbookId=:workbookId and t.order =:taskOrder")
    List<Long> findAnyNotAssignedWithConcreteOrder(Pageable limit, long workbookId, int taskOrder);

    @Query("SELECT t.id FROM TaskImpl t where t.stationId=:stationId and t.isCompleted=false")
    List<Long> findAnyActiveForStationId(Pageable limit, long stationId);

    @Query("SELECT count(t) FROM TaskImpl t where t.workbookId=:workbookId and t.order =:taskOrder")
    Long countWithConcreteOrder(long workbookId, int taskOrder);

    @Query("SELECT t FROM TaskImpl t where t.stationId=:stationId and t.resultReceived=false and " +
            " t.execState =:execState and (:mills - result_resource_scheduled_on > 15000) ")
    List<Task> findForMissingResultResources(long stationId, long mills, int execState);

    @Transactional(readOnly = true)
    // execState>1 --> 1==Enums.TaskExecState.IN_PROGRESS
    @Query("SELECT t FROM TaskImpl t, ExperimentTaskFeature tef " +
            "where t.id=tef.taskId and tef.featureId=:featureId and " +
            " t.execState > 1")
    List<Task> findByIsCompletedIsTrueAndFeatureId(long featureId);


    // !!! class must not be inner class
    @Transactional(readOnly = true)
    @Query("SELECT new aiai.api.v1.data.TaskWIthType(t, tef.taskType) FROM TaskImpl t, ExperimentTaskFeature tef " +
            "where t.id=tef.taskId and tef.featureId=:featureId order by t.id asc ")
    Slice<TaskWIthType> findPredictTasks(Pageable pageable, long featureId);


    @Query(nativeQuery = true, value = "select z.* "+
            "from ( "+
            "           SELECT count(*) count, t.TASK_ORDER "+
            "           FROM aiai_task t\n"+
            "           where t.WORKBOOK_ID =:workbookId "+
            "           group by t.TASK_ORDER "+
            "     ) z "+
            "order by z.TASK_ORDER asc")
    List<Object[]> getCountPerOrder(Long workbookId);

}
