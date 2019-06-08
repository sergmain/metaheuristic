/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.plan;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.api.v1.launchpad.Plan;
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.launchpad.repositories.PlanRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Profile("launchpad")
public class PlanCache {

    private final PlanRepository planRepository;

    public PlanCache(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @CacheEvict(value = Consts.PLANS_CACHE, key = "#result.id")
    public PlanImpl save(PlanImpl plan) {
        return planRepository.save(plan);
    }

    @Cacheable(cacheNames = Consts.PLANS_CACHE, unless="#result==null")
    public PlanImpl findById(long id) {
        return planRepository.findById(id).orElse(null);
    }

    @CacheEvict(cacheNames = {Consts.PLANS_CACHE}, key = "#plan.id")
    public void delete(Plan plan) {
        try {
            planRepository.deleteById(plan.getId());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error", e);
        }
    }

    @CacheEvict(cacheNames = {Consts.PLANS_CACHE}, key = "#id")
    public void deleteById(Long id) {
        try {
            planRepository.deleteById(id);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error", e);
        }
    }
}
