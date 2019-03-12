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

package aiai.ai.launchpad.flow;

import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.repositories.FlowRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
@Profile("launchpad")
@Slf4j
public class FlowCache {

    private final FlowRepository flowRepository;

    public FlowCache(FlowRepository flowRepository) {
        this.flowRepository = flowRepository;
    }

//    @CachePut(cacheNames = "flows", key = "#result.id")
    @CacheEvict(value = "flows", key = "#result.id")
    public Flow save(Flow flow) {
        return flowRepository.save(flow);
    }

    @Cacheable(cacheNames = "flows", unless="#result==null")
    public Flow findById(long id) {
        return flowRepository.findById(id).orElse(null);
    }

//    @CacheEvict(cacheNames = {"flows"}, allEntries=true)
    @CacheEvict(cacheNames = {"flows"}, key = "#flow.id")
    public void delete(Flow flow) {
        try {
            flowRepository.delete(flow);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error", e);
        }
    }

//    @CacheEvict(cacheNames = {"flows"}, allEntries=true)
    @CacheEvict(cacheNames = {"flows"}, key = "#id")
    public void deleteById(Long id) {
        try {
            flowRepository.deleteById(id);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error", e);
        }
    }
}
