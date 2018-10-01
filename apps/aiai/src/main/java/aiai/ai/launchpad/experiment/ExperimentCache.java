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
package aiai.ai.launchpad.experiment;

import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.repositories.ExperimentRepository;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
public class ExperimentCache {

    private final ExperimentRepository experimentRepository;

    public ExperimentCache(ExperimentRepository experimentRepository) {
        this.experimentRepository = experimentRepository;
    }

    @CachePut(cacheNames = "experiments", key = "#experiment.id")
    public void save(Experiment experiment) {
        experimentRepository.save(experiment);
    }

    @Cacheable(cacheNames = "experiments")
    public Experiment findById(long id) {
        return experimentRepository.findById(id).orElse(null);
    }
}
