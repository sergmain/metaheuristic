/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.mhbp.repositories;

import ai.metaheuristic.ai.mhbp.beans.Chapter;
import ai.metaheuristic.ai.mhbp.data.SimpleChapterUid;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 4/27/2023
 * Time: 3:19 AM
 */
@Repository
@Transactional
@Profile("dispatcher")
public interface ChapterRepository extends CrudRepository<Chapter, Long> {

    @Nullable
    Chapter findByKbIdAndCode(long kbId, String code);

    @Transactional(readOnly = true)
    @Query(value= "select new ai.metaheuristic.mhbp.data.SimpleChapterUid(a.id, a.code, a.promptCount) " +
                  " from Chapter a where a.kbId in(:kbIds)")
    List<SimpleChapterUid> findAllByKbIds(List<Long> kbIds);
}
