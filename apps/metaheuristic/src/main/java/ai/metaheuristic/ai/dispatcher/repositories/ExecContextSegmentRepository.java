/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.repositories;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextSegment;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Segments of ExecContexts (041-EXEC-CONTEXT-SEGMENTS-PLAN, decision 8).
 */
@Repository
@Profile("dispatcher")
public interface ExecContextSegmentRepository extends CrudRepository<ExecContextSegment, Long> {

    @Nullable
    @Query(value="select s from ExecContextSegment s where s.execContextId=:execContextId and s.lineCtxId=:lineCtxId")
    ExecContextSegment findByExecContextIdAndLineCtxId(Long execContextId, String lineCtxId);

    @Query(value="select s.id from ExecContextSegment s where s.execContextId=:execContextId")
    List<Long> findIdsByExecContextId(Long execContextId);

    /**
     * {@code LINE_CTX_ID}s matching {@code pattern}. With {@code pattern = base + "#%"} these are exactly the lines at
     * level {@code base} that start a segment: a ctx holds one {@code #}, so text before it is the level
     * ({@code ContextUtils.getLevel}), and ctx text never holds {@code %}, {@code _} or {@code \}.
     */
    @Query(value="select s.lineCtxId from ExecContextSegment s where s.execContextId=:execContextId and s.lineCtxId like :pattern")
    List<String> findLineCtxIdsByExecContextIdAndLineCtxIdLike(Long execContextId, String pattern);
}
