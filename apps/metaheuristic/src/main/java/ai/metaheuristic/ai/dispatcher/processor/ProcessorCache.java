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

package ai.metaheuristic.ai.dispatcher.processor;

import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 5/29/2019
 * Time: 7:25 PM
 */

@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ProcessorCache {

    private final ProcessorRepository processorRepository;

    public Processor save(@NonNull Processor processor) {
        TxUtils.checkTxExists();
        log.debug("#457.010 save processor, id: #{}, processor: {}", processor.id, processor);
        return processorRepository.save(processor);
    }

    public void delete(@NonNull Processor processor) {
        TxUtils.checkTxExists();
        try {
            processorRepository.delete(processor);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#457.030 Error deleting of processor by object", e);
        }
    }

    public void delete(Long processorId) {
        TxUtils.checkTxExists();
        try {
            processorRepository.deleteById(processorId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#457.050 Error deleting of processor by id", e);
        }
    }

    public void deleteById(Long processorId) {
        TxUtils.checkTxExists();
        try {
            processorRepository.deleteById(processorId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#457.070 Error deleting of processor by id", e);
        }
    }

    @Nullable
    public Processor findById(Long id) {
        return processorRepository.findById(id).orElse(null);
    }
}
