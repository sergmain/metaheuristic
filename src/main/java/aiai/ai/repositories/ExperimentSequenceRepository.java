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

package aiai.ai.repositories;

import aiai.ai.beans.Experiment;
import aiai.ai.beans.ExperimentSequence;
import aiai.ai.beans.ExperimentSnippet;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public interface ExperimentSequenceRepository extends CrudRepository<ExperimentSequence, Long> {

    @Transactional(readOnly = true)
    List<ExperimentSequence> findByExperimentIdAndFeatureId(long experiimentId, long featureId);

    @Transactional(readOnly = true)
    Slice<ExperimentSequence> findAllByStationIdIsNullAndFeatureId(Pageable pageable, long featureId);

/*
    @Transactional(readOnly = true)
    @Query("SELECT r FROM ExperimentSequence r " +
            "where r.stationId is not null and r.completed=false and numberOfCopies<:maxCopies and r.id  ")
    ExperimentSequence findTop1ByStationIdIsNotNullAndIsCompletedIsFalse(Pageable limit);
*/
    @Transactional(readOnly = true)
    ExperimentSequence findTop1ByStationIdIsNotNullAndIsCompletedIsFalseAndFeatureId(Long featureId);

    @Transactional(readOnly = true)
    ExperimentSequence findTop1ByIsCompletedIsFalseAndFeatureId(Long featureId);

}
