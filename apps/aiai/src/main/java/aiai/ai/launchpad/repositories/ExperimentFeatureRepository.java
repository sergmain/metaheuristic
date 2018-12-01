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

import aiai.ai.launchpad.beans.ExperimentFeature;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Profile("launchpad")
public interface ExperimentFeatureRepository extends CrudRepository<ExperimentFeature, Long> {

    @Transactional(readOnly = true)
    List<ExperimentFeature> findByExperimentId(Long experimentId);

/*
    @Transactional(readOnly = true)
    @Query("SELECT f FROM ExperimentFeature f, Experiment e where f.experimentId=e.id and f.isFinished=false and f.isInProgress=true and e.execState=:state")
    List<ExperimentFeature> findTop1ByIsFinishedIsFalseAndIsInProgressIsTrue(Pageable limit, int state);

    // continue process the same feature
    @Transactional(readOnly = true)
    @Query("SELECT f FROM ExperimentFeature f, Experiment e where f.experimentId=e.id and f.isFinished=false and f.isInProgress=true and e.execState=:state and e.id=:experimentId")
    List<ExperimentFeature> findTop1ByIsFinishedIsFalseAndIsInProgressIsTrueAndExperimentId(Pageable limit, int state, long experimentId);

    // find new feature for processing
    @Transactional(readOnly = true)
    @Query("SELECT f FROM ExperimentFeature f, Experiment e where f.experimentId=e.id and f.isInProgress=false and f.isFinished=false ")
    List<ExperimentFeature> findTop1ByIsFinishedIsFalseAndIsInProgressIsFalse(Pageable limit);

    @Transactional(readOnly = true)
    @Query("SELECT f FROM ExperimentFeature f, Experiment e where f.experimentId=e.id and e.isLaunched=true and f.isFinished=false ")
    List<ExperimentFeature> findAllForLaunchedExperimentsAndNotFinishedFeatures();

    @Transactional(readOnly = true)
    @Query("SELECT f FROM ExperimentFeature f, Experiment e where f.experimentId=e.id and e.isLaunched=true and e.execState=:state")
    List<ExperimentFeature> findAllForLaunchedExperiments(int state);

    @Transactional(readOnly = true)
    @Query("SELECT f FROM ExperimentFeature f, Experiment e where f.experimentId=e.id and e.isLaunched=true and e.execState<>:state")
    List<ExperimentFeature> findAllForActiveExperiments(int state);
*/


    @Transactional
    void deleteByExperimentId(long experimentId);

}
