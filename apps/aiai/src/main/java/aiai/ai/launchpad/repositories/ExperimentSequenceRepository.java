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
import aiai.ai.launchpad.beans.ExperimentSequence;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Profile("launchpad")
public interface ExperimentSequenceRepository extends CrudRepository<ExperimentSequence, Long> {

    @Transactional(readOnly = true)
    Slice<ExperimentSequence> findAll(Pageable pageable);

    List<ExperimentSequence> findByExperimentId(long experimentId);

    @Transactional(readOnly = true)
    List<ExperimentSequence> findByIsCompletedIsTrueAndFeatureId(long featureId);

    @Transactional(readOnly = true)
    Slice<ExperimentSequence> findByIsCompletedIsTrueAndFeatureId(Pageable pageable, long featureId);

    @Transactional(readOnly = true)
    List<ExperimentSequence> findByExperimentIdAndFeatureId(long experiimentId, long featureId);

    @Transactional(readOnly = true)
    Slice<ExperimentSequence> findAllByStationIdIsNullAndFeatureId(Pageable pageable, long featureId);

    @Transactional(readOnly = true)
    Slice<ExperimentSequence> findAllByStationIdIsNullAndFeatureIdAndExperimentId(Pageable pageable, long featureId, long experimentId);

    @Transactional(readOnly = true)
    ExperimentSequence findTop1ByStationIdAndIsCompletedIsFalseAndFeatureId(long stationId, long featureId);

    @Transactional(readOnly = true)
    List<ExperimentSequence> findByStationIdAndIsCompletedIsFalse(long stationId);

    @Transactional(readOnly = true)
    ExperimentSequence findTop1ByFeatureId(Long featureId);

    @Transactional(readOnly = true)
    ExperimentSequence findTop1ByIsCompletedIsFalseAndFeatureId(Long featureId);

    @Transactional(readOnly = true)
    ExperimentSequence findTop1ByIsAllSnippetsOkIsTrueAndFeatureId(long featureId);

    @Transactional
    void deleteByExperimentId(long experimentId);

    @Transactional(readOnly = true)
    @Query("SELECT f FROM ExperimentSequence s, ExperimentFeature f, Experiment e  where s.stationId=:stationId and s.featureId=f.id and f.experimentId=e.id and  " +
            "f.isFinished=false and f.isInProgress=true and e.isLaunched=true and e.execState=:state")
    List<ExperimentFeature> findAnyStartedButNotFinished(Pageable limit, long stationId, int state);

    @Transactional(readOnly = true)
    List<ExperimentSequence> findByStationIdAndIsCompletedIsFalse(Long stationId);


}
