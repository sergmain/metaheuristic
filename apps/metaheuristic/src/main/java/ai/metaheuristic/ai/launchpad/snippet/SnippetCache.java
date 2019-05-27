/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

import ai.metaheuristic.ai.launchpad.beans.Snippet;
import ai.metaheuristic.ai.launchpad.repositories.SnippetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@Profile("launchpad")
@Slf4j
public class SnippetCache {

    private final SnippetRepository snippetRepository;

    public SnippetCache(SnippetRepository snippetRepository) {
        this.snippetRepository = snippetRepository;
    }

    @CacheEvict(cacheNames = "snippets", key = "#result.id")
    public Snippet save(Snippet snippet) {
        return snippetRepository.save(snippet);
    }

    @CacheEvict(cacheNames = {"snippets"}, key = "#snippet.id")
    public void delete(Snippet snippet) {
        try {
            snippetRepository.delete(snippet);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error deleting of snippet by object", e);
        }
    }

    @CacheEvict(cacheNames = {"snippets"}, key = "#snippetId")
    public void delete(Long snippetId) {
        try {
            snippetRepository.deleteById(snippetId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error deleting of snippet by id", e);
        }
    }

    @Cacheable(cacheNames = "snippets", unless="#result==null")
    public Snippet findById(Long id) {
        return snippetRepository.findById(id).orElse(null);
    }

}
