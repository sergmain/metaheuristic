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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class ExecContextCache {

    private final ExecContextRepository execContextRepository;

//    @CacheEvict(cacheNames = {Consts.EXEC_CONTEXT_CACHE}, allEntries = true)
    public void clearCache() {
        TxUtils.checkTxExists();
    }

//    @CachePut(cacheNames = {Consts.EXEC_CONTEXT_CACHE}, key = "#result.id")
    public ExecContextImpl save(ExecContextImpl execContext) {
        TxUtils.checkTxExists();
        // execContext.id is null for a newly created bean
        if (execContext.id!=null) {
            ExecContextSyncService.checkWriteLockPresent(execContext.id);
        }
/*
        if (log.isDebugEnabled()) {
            log.debug("#461.010 save execContext, Thread {}, id: #{}, ver: {}, execContext: {}",
                    Thread.currentThread().getId(), execContext.id, execContext.version, execContext);
            try {
                throw new RuntimeException("stacktrace");
            }
            catch(RuntimeException e) {
                log.debug("stacktrace", e);
            }
        }
*/
        return execContextRepository.save(execContext);
    }

//    @CacheEvict(cacheNames = {Consts.EXEC_CONTEXT_CACHE}, key = "#execContext.id")
    public void delete(ExecContextImpl execContext) {
        TxUtils.checkTxExists();
        try {
            execContextRepository.deleteById(execContext.id);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#461.030 Error deleting of execContext by object, {}", e.toString());
        }
    }

//    @CacheEvict(cacheNames = {Consts.EXEC_CONTEXT_CACHE}, key = "#execContextId")
    public void delete(Long execContextId) {
        TxUtils.checkTxExists();
        try {
            execContextRepository.deleteById(execContextId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#461.050 Error deleting of execContext by id, {}", e.toString());
        }
    }

//    @CacheEvict(cacheNames = {Consts.EXEC_CONTEXT_CACHE}, key = "#execContextId")
    public void deleteById(Long execContextId) {
        TxUtils.checkTxExists();
        try {
            execContextRepository.deleteById(execContextId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#461.070 Error deleting of execContext by id, {}", e.toString());
        }
    }

    @Nullable
//    @Cacheable(cacheNames = {Consts.EXEC_CONTEXT_CACHE}, unless="#result==null")
    public ExecContextImpl findById(Long id) {
        return execContextRepository.findById(id).orElse(null);
    }
}
