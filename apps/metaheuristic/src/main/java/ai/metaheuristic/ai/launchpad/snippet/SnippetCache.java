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
package ai.metaheuristic.ai.launchpad.snippet;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.launchpad.beans.Function;
import ai.metaheuristic.ai.launchpad.repositories.SnippetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@Profile("launchpad")
@Slf4j
@RequiredArgsConstructor
public class SnippetCache {

    private final SnippetRepository snippetRepository;

    @CacheEvict(cacheNames = {Consts.SNIPPETS_CACHE}, key = "#result.id")
    public Function save(Function function) {
        function.reset();
        return snippetRepository.saveAndFlush(function);
    }

    @CacheEvict(cacheNames = {Consts.SNIPPETS_CACHE}, key = "#function.id")
    public void delete(Function function) {
        try {
            snippetRepository.delete(function);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error deleting of function by object", e);
        }
    }

    @CacheEvict(cacheNames = {Consts.SNIPPETS_CACHE}, key = "#snippetId")
    public void delete(Long snippetId) {
        try {
            snippetRepository.deleteById(snippetId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error deleting of function by id", e);
        }
    }

    @Cacheable(cacheNames = {Consts.SNIPPETS_CACHE}, unless="#result==null")
    public Function findById(Long id) {
        return snippetRepository.findById(id).orElse(null);
    }

}
