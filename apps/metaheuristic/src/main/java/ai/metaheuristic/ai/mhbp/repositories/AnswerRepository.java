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

import ai.metaheuristic.ai.mhbp.beans.Answer;
import ai.metaheuristic.ai.mhbp.data.SimpleAnswerStats;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 3/22/2023
 * Time: 1:58 AM
 */
@Repository
@Transactional
@Profile("dispatcher")
public interface AnswerRepository extends CrudRepository<Answer, Long> {

    @Transactional(readOnly = true)
    @Query(value="select new ai.metaheuristic.mhbp.data.SimpleAnswerStats(a.id, a.sessionId, a.chapterId, a.total, a.failed, a.systemError) " +
                 " from Answer a where a.sessionId in (:sessionIds)")
    List<SimpleAnswerStats> getStatusesJpql(List<Long> sessionIds);

    // status - public enum AnswerStatus { normal(0), fail(1), error(2);
    @Transactional(readOnly = true)
    @Query(value="select s from Answer s where s.sessionId=:sessionId")
    Page<Answer> findAllBySessionId(Pageable pageable, Long sessionId);

}