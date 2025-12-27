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
package ai.metaheuristic.ai.dispatcher.function;

import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.jspecify.annotations.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class FunctionCache {

    private final FunctionRepository functionRepository;

    public Function save(Function function) {
        TxUtils.checkTxExists();
        return functionRepository.save(function);
    }

    public void delete(Function function) {
        TxUtils.checkTxExists();
        try {
            functionRepository.delete(function);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error deleting of function by object", e);
        }
    }

    public void delete(Long functionId) {
        TxUtils.checkTxExists();
        try {
            functionRepository.deleteById(functionId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error deleting of function by id", e);
        }
    }

    @Nullable
    public Function findById(Long id) {
        return functionRepository.findByIdNullable(id);
    }

}
