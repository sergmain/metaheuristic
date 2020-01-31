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
package ai.metaheuristic.ai.launchpad.repositories;

import ai.metaheuristic.ai.launchpad.beans.Snippet;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Repository
@Transactional
@Profile("launchpad")
public interface SnippetRepository extends JpaRepository<Snippet, Long> {

    @Transactional(readOnly = true)
    Snippet findByCode(String code);

    @Transactional(readOnly = true)
    @Query(value="select b.id from Snippet b where b.code=:code")
    Long findIdByCode(String code);

    @Transactional(readOnly = true)
    @Query(value="select b.id from Snippet b where b.code in :codes")
    List<Long> findIdsByCodes(List<String> codes);

    @Transactional
    @Query(value="select b from Snippet b where b.code=:code")
    Snippet findByCodeForUpdate(String code);

    @Transactional(readOnly = true)
    @Query(value="select b.id from Snippet b")
    List<Long> findAllIds();

    @Transactional(readOnly = true)
    @Query(value="select b.code from Snippet b")
    List<String> findAllSnippetCodes();
}
