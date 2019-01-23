/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.launchpad.snippet;

import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.repositories.SnippetRepository;
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

//    @CachePut(cacheNames = "snippets", key = "#result.id")
    @CacheEvict(cacheNames = "snippets", key = "#result.id")
    public Snippet save(Snippet snippet) {
        return snippetRepository.save(snippet);
    }

    @CacheEvict(cacheNames = {"snippets"}, key = "#snippet.id")
    public void delete(Snippet snippet) {
        try {
            snippetRepository.delete(snippet);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error deleting of snippet by object, {}", e);
        }
    }

//    @CacheEvict(cacheNames = {"snippets", "snippetsByName"}, allEntries=true)
    @CacheEvict(cacheNames = {"snippets"}, key = "#snippetId")
    public void delete(long snippetId) {
        try {
            snippetRepository.deleteById(snippetId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error deleting of snippet by id, {}", e);
        }
    }

    @Cacheable(cacheNames = "snippets", unless="#result==null")
    public Snippet findById(long id) {
        return snippetRepository.findById(id).orElse(null);
    }

}
