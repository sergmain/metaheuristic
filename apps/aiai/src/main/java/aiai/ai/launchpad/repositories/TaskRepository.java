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
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Profile("launchpad")
public interface TaskRepository extends CrudRepository<Task, Long> {

    @Transactional(readOnly = true)
    Slice<Task> findAll(Pageable pageable);

    @Transactional(readOnly = true)
    List<Task> findByStationIdAndIsCompletedIsFalse(long stationId);

    @Transactional
    void deleteByFlowInstanceId(long flowInstanceId);

    @Transactional(readOnly = true)
    List<Task> findByFlowInstanceId(long flowInstanceId);

    @Transactional(readOnly = true)
    Slice<Task> findByIsCompletedIsTrueAndFeatureId(Pageable pageable, long featureId);

/*

    List<Task> findByExperimentId(long experimentId);

    @Transactional(readOnly = true)
    List<Task> findByIsCompletedIsTrueAndFeatureId(long featureId);

    @Transactional(readOnly = true)
    List<Task> findByExperimentIdAndFeatureId(long experiimentId, long featureId);

    @Transactional(readOnly = true)
    Slice<Task> findAllByStationIdIsNullAndFeatureId(Pageable pageable, long featureId);

    @Transactional(readOnly = true)
    Slice<Task> findAllByStationIdIsNullAndFeatureIdAndExperimentId(Pageable pageable, long featureId, long experimentId);

    @Transactional(readOnly = true)
    Task findTop1ByStationIdAndIsCompletedIsFalseAndFeatureId(long stationId, long featureId);

    @Transactional(readOnly = true)
    Task findTop1ByFeatureId(Long featureId);

    @Transactional(readOnly = true)
    Task findTop1ByIsCompletedIsFalseAndFeatureId(Long featureId);

    @Transactional(readOnly = true)
    Task findTop1ByIsAllSnippetsOkIsTrueAndFeatureId(long featureId);

    @Transactional
    void deleteByExperimentId(long experimentId);
*/
/*

    @Transactional(readOnly = true)
    @Query("SELECT f FROM Task s, ExperimentFeature f, Experiment e  where s.stationId=:stationId and s.featureId=f.id and f.experimentId=e.id and  " +
            "f.isFinished=false and f.isInProgress=true and e.isLaunched=true and e.execState=:state")
    List<ExperimentFeature> findAnyStartedButNotFinished(Pageable limit, long stationId, int state);
*/

/*
    @Transactional(readOnly = true)
    List<Task> findByStationIdAndIsCompletedIsFalse(Long stationId);
*/


}
