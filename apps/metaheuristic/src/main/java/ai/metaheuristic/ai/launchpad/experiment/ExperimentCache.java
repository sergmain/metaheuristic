/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package ai.metaheuristic.ai.launchpad.experiment;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.launchpad.beans.Experiment;
import ai.metaheuristic.ai.launchpad.repositories.ExperimentRepository;
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

    @CacheEvict(value = {Consts.EXPERIMENTS_CACHE}, key = "#result.id")
    public Experiment save(Experiment experiment) {
        // noinspection UnusedAssignment
        Experiment save=null;
        //noinspection CaughtExceptionImmediatelyRethrown
        try {
            save = experimentRepository.saveAndFlush(experiment);
            return save;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw e;
        }
    }

    @Cacheable(cacheNames = {Consts.EXPERIMENTS_CACHE}, unless="#result==null")
    public Experiment findById(long id) {
        return experimentRepository.findById(id).orElse(null);
    }

    @CacheEvict(cacheNames = {Consts.EXPERIMENTS_CACHE}, key = "#experiment.id")
    public void delete(Experiment experiment) {
        if (experiment==null || experiment.id==null) {
            return;
        }
        try {
            experimentRepository.delete(experiment);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error", e);
        }
    }

    @CacheEvict(cacheNames = {Consts.EXPERIMENTS_CACHE}, key = "#id")
    public void invalidate(Long id) {
        //
    }

    @CacheEvict(cacheNames = {Consts.EXPERIMENTS_CACHE}, key = "#id")
    public void deleteById(Long id) {
        if (id==null) {
            return;
        }
        try {
            experimentRepository.deleteById(id);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error", e);
        }
    }
}
