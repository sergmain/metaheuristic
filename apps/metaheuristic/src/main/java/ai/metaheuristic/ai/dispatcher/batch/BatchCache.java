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

package ai.metaheuristic.ai.mh.dispatcher..batch;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.mh.dispatcher..beans.Batch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
@Profile("mh.dispatcher.")
@Slf4j
public class BatchCache {

    private final BatchRepository batchRepository;

    public BatchCache(BatchRepository batchRepository) {
        this.batchRepository = batchRepository;
    }

    @CacheEvict(value = {Consts.BATCHES_CACHE}, key = "#result.id")
    public Batch save(Batch batch) {
        if (batch==null) {
            return null;
        }
        log.info("#459.010 save batch, id: #{}, batch: {}", batch.id, batch);
        return batchRepository.saveAndFlush(batch);
    }

    @Cacheable(cacheNames = {Consts.BATCHES_CACHE}, unless="#result==null")
    public Batch findById(Long id) {
        return batchRepository.findById(id).orElse(null);
    }

    @CacheEvict(cacheNames = {Consts.BATCHES_CACHE}, key = "#batch.id")
    public void delete(Batch batch) {
        if (batch==null) {
            return;
        }
        try {
            batchRepository.deleteById(batch.getId());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("#459.030 Error while deleting, batch in db: " + batchRepository.findById(batch.getId()), e);
        }
    }

    @CacheEvict(cacheNames = {Consts.BATCHES_CACHE}, key = "#id")
    public void deleteById(Long id) {
        if (id==null) {
            return;
        }
        try {
            batchRepository.deleteById(id);
        } catch (ObjectOptimisticLockingFailureException e) {

            log.warn("#459.050 Error while deletingById, batch in db: " + batchRepository.findById(id), e);
        }
    }

    @CacheEvict(cacheNames = {Consts.BATCHES_CACHE}, key = "#id")
    public void evictById(Long id) {
        //
    }
}
