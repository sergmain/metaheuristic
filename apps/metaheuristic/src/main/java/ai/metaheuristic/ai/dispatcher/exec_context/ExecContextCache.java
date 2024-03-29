/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
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
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextCache {

    private final ExecContextRepository execContextRepository;

    public ExecContextImpl save(ExecContextImpl execContext) {
        TxUtils.checkTxExists();
        // execContext.id is null for a newly created bean
        if (execContext.id!=null) {
            ExecContextSyncService.checkWriteLockPresent(execContext.id);
        }
        final ExecContextImpl ec = execContextRepository.save(execContext);
        return ec;

    }

    public void delete(ExecContextImpl execContext) {
        TxUtils.checkTxExists();
        try {
            execContextRepository.deleteById(execContext.id);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("461.030 Error deleting of execContext by object, {}", e.toString());
        }
    }

    public void delete(Long execContextId) {
        TxUtils.checkTxExists();
        try {
            execContextRepository.deleteById(execContextId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#461.050 Error deleting of execContext by id, {}", e.toString());
        }
    }

    public void deleteById(Long execContextId) {
        TxUtils.checkTxExists();
        try {
            execContextRepository.deleteById(execContextId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#461.070 Error deleting of execContext by id, {}", e.toString());
        }
    }

    @Nullable
    public ExecContextImpl findById(Long id) {
        return findById(id, false);
    }

    @Nullable
    public ExecContextImpl findById(Long id, boolean detached) {
        if (detached) {
            final ExecContextImpl execContext = execContextRepository.findByIdNullable(id);
            return execContext;
        }
        return execContextRepository.findById(id).orElse(null);
    }
}
