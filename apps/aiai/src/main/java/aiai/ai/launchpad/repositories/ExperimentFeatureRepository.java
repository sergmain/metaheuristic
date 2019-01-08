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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Profile("launchpad")
public interface ExperimentFeatureRepository extends CrudRepository<ExperimentFeature, Long> {

    @Transactional(readOnly = true)
    List<ExperimentFeature> findByExperimentIdOrderByMaxValueDesc(Long experimentId);

    @Transactional(readOnly = true)
    List<ExperimentFeature> findByExperimentId(Long experimentId);

    @Query("SELECT f.id, f.resourceCodes FROM ExperimentFeature f where f.experimentId=:experimentId")
    List<Object[]> getAsExperimentFeatureSimpleByExperimentId(Long experimentId);

    @Query("SELECT f.checksumIdCodes FROM ExperimentFeature f where f.experimentId=:experimentId")
    List<String> getChecksumIdCodesByExperimentId(long experimentId);

    @Transactional
    void deleteByExperimentId(long experimentId);

}
