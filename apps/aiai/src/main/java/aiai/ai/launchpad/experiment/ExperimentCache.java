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
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
@Profile("launchpad")
@Slf4j
public class ExperimentCache {

    private final ExperimentRepository experimentRepository;

    public ExperimentCache(ExperimentRepository experimentRepository) {
        this.experimentRepository = experimentRepository;
    }

//    @CachePut(cacheNames = "experiments", key = "#result.id")
//    @Cacheable(cacheNames = "experiments", unless="#result==null")
    @CacheEvict(value = "experiments", key = "#result.id")
    public Experiment save(Experiment experiment) {
        // noinspection UnusedAssignment
        Experiment save=null;
        //noinspection CaughtExceptionImmediatelyRethrown
        try {
            save = experimentRepository.save(experiment);
            return save;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw e;
        }
    }

    @Cacheable(cacheNames = "experiments", unless="#result==null")
    public Experiment findById(long id) {
        return experimentRepository.findById(id).orElse(null);
    }

    @CacheEvict(cacheNames = {"experiments"}, key = "#experiment.id")
    public void delete(Experiment experiment) {
        try {
            experimentRepository.delete(experiment);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error", e);
        }
    }

    @CacheEvict(cacheNames = {"experiments"}, key = "#id")
    public void deleteById(Long id) {
        try {
            experimentRepository.deleteById(id);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error", e);
        }
    }
}
