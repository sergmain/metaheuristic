/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.plan;

import metaheuristic.api.v1.launchpad.Plan;
import aiai.ai.launchpad.beans.PlanImpl;
import aiai.ai.launchpad.repositories.PlanRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
@Profile("launchpad")
@Slf4j
public class PlanCache {

    private final PlanRepository planRepository;

    public PlanCache(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

//    @CachePut(cacheNames = "plans", key = "#result.id")
    @CacheEvict(value = "plans", key = "#result.id")
    public PlanImpl save(PlanImpl plan) {
        // TODO 2019.05.03 need to deal with such error:
        // org.hibernate.StaleObjectStateException: Row was updated or deleted by another transaction
        // (or unsaved-value mapping was incorrect) : [aiai.ai.launchpad.beans.Plan#349]
        return planRepository.save(plan);
    }

    @Cacheable(cacheNames = "plans", unless="#result==null")
    public PlanImpl findById(long id) {
        return planRepository.findById(id).orElse(null);
    }

//    @CacheEvict(cacheNames = {"plans"}, allEntries=true)
    @CacheEvict(cacheNames = {"plans"}, key = "#plan.id")
    public void delete(Plan plan) {
        try {
            planRepository.deleteById(plan.getId());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error", e);
        }
    }

//    @CacheEvict(cacheNames = {"plans"}, allEntries=true)
    @CacheEvict(cacheNames = {"plans"}, key = "#id")
    public void deleteById(Long id) {
        try {
            planRepository.deleteById(id);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error", e);
        }
    }
}
