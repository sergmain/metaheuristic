/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */

package aiai.ai.launchpad.repositories;

import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.beans.TaskSimple;
import aiai.ai.launchpad.experiment.task.TaskWIthType;
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
public interface TaskRepository extends CrudRepository<Task, Long> {

    @Transactional(readOnly = true)
    Slice<Task> findAll(Pageable pageable);

    @Query(value="select t.id, t.flowInstanceId from Task t")
    Stream<Object[]> findAllAsTaskSimple(Pageable pageable);

    @Transactional(readOnly = true)
    List<Task> findByStationIdAndResultReceivedIsFalse(long stationId);

    @Transactional
    void deleteByFlowInstanceId(long flowInstanceId);

    @Transactional
    @Query(value="select t.id, t.params from Task t where t.flowInstanceId=:flowInstanceId")
    Stream<Object[]> findByFlowInstanceId(long flowInstanceId);

    @Query("SELECT t FROM Task t where t.stationId is null and t.flowInstanceId=:flowInstanceId and t.order =:taskOrder")
    Slice<Task> findForAssigning(Pageable pageable, long flowInstanceId, int taskOrder);

    @Query("SELECT t FROM Task t where t.stationId is not null and t.flowInstanceId=:flowInstanceId and t.order =:taskOrder")
    List<Task> findWithConcreteOrder(long flowInstanceId, int taskOrder);

    @Query("SELECT count(t) FROM Task t where t.flowInstanceId=:flowInstanceId and t.order =:taskOrder")
    Long countWithConcreteOrder(long flowInstanceId, int taskOrder);

    @Query("SELECT t FROM Task t where t.stationId=:stationId and t.resultReceived=false and " +
            " t.execState =:execState and (:mills - result_resource_scheduled_on > 15000) ")
    List<Task> findForMissingResultResources(long stationId, long mills, int execState);



/*
    @Transactional(readOnly = true)
    @Query("SELECT t FROM Task t, ExperimentTaskFeature tef " +
            "where t.id=tef.taskId and tef.featureId=:featureId and " +
            " t.execState > 1")
    Slice<Task> findByIsCompletedIsTrueAndFeatureId(Pageable pageable, long featureId);
*/

    @Transactional(readOnly = true)
    // execState>1 --> 1==Enums.TaskExecState.IN_PROGRESS
    @Query("SELECT t FROM Task t, ExperimentTaskFeature tef " +
            "where t.id=tef.taskId and tef.featureId=:featureId and " +
            " t.execState > 1")
    List<Task> findByIsCompletedIsTrueAndFeatureId(long featureId);


    @Transactional(readOnly = true)
    @Query("SELECT new aiai.ai.launchpad.experiment.task.TaskWIthType(t, tef.taskType) FROM Task t, ExperimentTaskFeature tef " +
            "where t.id=tef.taskId and tef.featureId=:featureId ")
    Slice<TaskWIthType> findPredictTasks(Pageable pageable, long featureId);
}
