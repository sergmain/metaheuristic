/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.beans.Batch;
import ai.metaheuristic.ai.dispatcher.repositories.BatchRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.jspecify.annotations.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class BatchCache {

    private final BatchRepository batchRepository;

    public Batch save(Batch batch) {
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
