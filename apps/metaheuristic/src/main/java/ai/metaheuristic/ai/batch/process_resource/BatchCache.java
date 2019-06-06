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

package ai.metaheuristic.ai.batch.process_resource;

import ai.metaheuristic.ai.batch.beans.Batch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
@Profile("launchpad")
@Slf4j
public class BatchCache {

    private static final String BATCHES_CACHE = "batches";

    private final BatchRepository batchRepository;

    public BatchCache(BatchRepository batchRepository) {
        this.batchRepository = batchRepository;
    }

    @CacheEvict(value = BATCHES_CACHE, key = "#result.id")
    public Batch save(Batch batch) {
        return batchRepository.save(batch);
    }

    @Cacheable(cacheNames = BATCHES_CACHE, unless="#result==null")
    public Batch findById(Long id) {
        return batchRepository.findById(id).orElse(null);
    }

    @CacheEvict(cacheNames = {BATCHES_CACHE}, key = "#batch.id")
    public void delete(Batch batch) {
        if (batch==null) {
            return;
        }
        try {
            batchRepository.deleteById(batch.getId());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error while deleting, batch in db: " + batchRepository.findById(batch.getId()), e);
        }
    }

    @CacheEvict(cacheNames = {BATCHES_CACHE}, key = "#id")
    public void deleteById(Long id) {
        if (id==null) {
            return;
        }
        try {
            batchRepository.deleteById(id);
        } catch (ObjectOptimisticLockingFailureException e) {

            log.warn("Error while deletingById, batch in db: " + batchRepository.findById(id), e);
        }
    }

    @CacheEvict(cacheNames = {BATCHES_CACHE}, key = "#id")
    public void evictById(Long id) {
        //
    }
}
