package aiai.ai.launchpad.flow;

import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.repositories.FlowRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
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

    @CachePut(cacheNames = "flows", key = "#result.id")
    public Flow save(Flow flow) {
        return flowRepository.save(flow);
    }

    @Cacheable(cacheNames = "flows", unless="#result==null")
    public Flow findById(long id) {
        return flowRepository.findById(id).orElse(null);
    }

    @CacheEvict(cacheNames = {"flows"}, allEntries=true)
    public void delete(Flow flow) {
        try {
            flowRepository.delete(flow);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error", e);
        }
    }

    @CacheEvict(cacheNames = {"flows"}, allEntries=true)
    public void deleteById(Long id) {
        try {
            flowRepository.deleteById(id);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error", e);
        }
    }
}
