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

package ai.metaheuristic.ai.launchpad.repositories;

import ai.metaheuristic.ai.launchpad.beans.SnippetBinaryData;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Blob;

/**
 * @author Serge
 * Date: 1/23/2020
 * Time: 9:31 PM
 */
@Repository
@Profile("launchpad")
public interface SnippetBinaryDataRepository extends CrudRepository<SnippetBinaryData, Long> {

    @Transactional(readOnly = true)
    @Query(value="select b.data from SnippetBinaryData b where b.snippetCode=:snippetCode")
    Blob getDataAsStreamByCode(String snippetCode);

    @Transactional
    @Query(value="select b from SnippetBinaryData b where b.snippetCode=:snippetCode")
    SnippetBinaryData findByCodeForUpdate(String snippetCode);

    @Transactional
    void deleteBySnippetCode(String snippetCode);
}
