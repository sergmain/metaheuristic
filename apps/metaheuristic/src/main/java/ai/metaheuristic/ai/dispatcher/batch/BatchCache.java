/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.batch;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.Batch;
import ai.metaheuristic.ai.dispatcher.repositories.BatchRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class BatchCache {

    private final BatchRepository batchRepository;

    public Batch save(@NonNull Batch batch) {
        TxUtils.checkTxExists();
        log.info("#459.010 save batch, id: #{}, batch: {}", batch.id, batch);
        return batchRepository.save(batch);
    }

    @Nullable
    public Batch findById(Long id) {
        return batchRepository.findById(id).orElse(null);
    }

    public void delete(@Nullable Batch batch) {
        TxUtils.checkTxExists();
        if (batch==null) {
            return;
        }
        try {
            batchRepository.deleteById(batch.getId());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("#459.030 Error while deleting, batch in db: " + batchRepository.findById(batch.getId()), e);
        }
    }

    public void deleteById(@Nullable Long id) {
        TxUtils.checkTxExists();
        if (id==null) {
            return;
        }
        try {
            batchRepository.deleteById(id);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("#459.050 Error while deletingById, batch in db: " + batchRepository.findById(id), e);
        }
    }
}
