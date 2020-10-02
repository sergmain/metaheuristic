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

package ai.metaheuristic.ai.dispatcher.source_code;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.dispatcher.SourceCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class SourceCodeCache {

    private final SourceCodeRepository sourceCodeRepository;

    @CachePut(value = {Consts.SOURCE_CODES_CACHE}, key = "#result.id")
    public SourceCodeImpl save(SourceCodeImpl sourceCode) {
        TxUtils.checkTxExists();
        return sourceCodeRepository.save(sourceCode);
    }

    @Nullable
    @Cacheable(cacheNames = {Consts.SOURCE_CODES_CACHE}, unless="#result==null")
    public SourceCodeImpl findById(Long id) {
        TxUtils.checkTxExists();
        return sourceCodeRepository.findById(id).orElse(null);
    }

    @CacheEvict(cacheNames = {Consts.SOURCE_CODES_CACHE}, key = "#sourceCode.id")
    public void delete(SourceCode sourceCode) {
        TxUtils.checkTxExists();
        if (sourceCode.getId()==null) {
            return;
        }
        try {
            sourceCodeRepository.deleteById(sourceCode.getId());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error", e);
        }
    }

    @CacheEvict(cacheNames = {Consts.SOURCE_CODES_CACHE}, key = "#id")
    public void deleteById(Long id) {
        TxUtils.checkTxExists();
        sourceCodeRepository.deleteById(id);
    }
}
