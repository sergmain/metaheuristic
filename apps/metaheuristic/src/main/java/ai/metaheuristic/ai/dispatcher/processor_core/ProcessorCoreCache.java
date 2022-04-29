/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.processor_core;

import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.beans.ProcessorCore;
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorCoreRepository;
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
 * Date: 4/29/2022
 * Time: 1:58 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ProcessorCoreCache {

    private final ProcessorCoreRepository processorCoreRepository;

    public ProcessorCore save(@NonNull Processor processor) {
        TxUtils.checkTxExists();
        log.debug("#457.010 save processor, id: #{}, processor: {}", processor.id, processor);
        return processorCoreRepository.save(processor);
    }

    public void delete(@NonNull ProcessorCore processor) {
        TxUtils.checkTxExists();
        try {
            processorCoreRepository.delete(processor);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#457.030 Error deleting of processor by object", e);
        }
    }

    public void delete(Long processorId) {
        TxUtils.checkTxExists();
        try {
            processorCoreRepository.deleteById(processorId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#457.050 Error deleting of processor by id", e);
        }
    }

    public void deleteById(Long processorId) {
        TxUtils.checkTxExists();
        try {
            processorCoreRepository.deleteById(processorId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#457.070 Error deleting of processor by id", e);
        }
    }

    @Nullable
    public ProcessorCore findById(Long id) {
        return processorCoreRepository.findById(id).orElse(null);
    }
}
