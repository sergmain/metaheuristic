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
package aiai.ai.station.repositories;

import aiai.ai.station.beans.StationExperimentSequenceOld;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public interface StationExperimentSequenceOldRepository extends CrudRepository<StationExperimentSequenceOld, Long> {

    List<StationExperimentSequenceOld> findAllByFinishedOnIsNull();
    List<StationExperimentSequenceOld> findAllByFinishedOnIsNull(Pageable pageable);

    List<StationExperimentSequenceOld> findAllByFinishedOnIsNotNull();
    List<StationExperimentSequenceOld> findAllByFinishedOnIsNotNullAndIsReportedIsFalse();

    StationExperimentSequenceOld findByExperimentSequenceId(long id);

}