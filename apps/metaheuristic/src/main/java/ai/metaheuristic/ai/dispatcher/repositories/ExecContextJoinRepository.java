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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextJoin;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Join vertices of ExecContexts (041-EXEC-CONTEXT-SEGMENTS-PLAN, decisions 8 and 9).
 */
@Repository
@Profile("dispatcher")
public interface ExecContextJoinRepository extends CrudRepository<ExecContextJoin, Long> {

    @Nullable
    @Query(value="select j from ExecContextJoin j where j.execContextId=:execContextId and j.joinTaskId=:joinTaskId")
    ExecContextJoin findByExecContextIdAndJoinTaskId(Long execContextId, Long joinTaskId);

    @Query(value="select j.id from ExecContextJoin j where j.execContextId=:execContextId")
    List<Long> findIdsByExecContextId(Long execContextId);

    /** Number of join records of an ExecContext (041 Phase 12: part of the change-detection version). */
    @Query(value="select count(j.id) from ExecContextJoin j where j.execContextId=:execContextId")
    long countByExecContextId(Long execContextId);

    /** Sum of the join records' {@code VERSION}s, null without join records (041 Phase 12). */
    @org.jspecify.annotations.Nullable
    @Query(value="select sum(j.version) from ExecContextJoin j where j.execContextId=:execContextId")
    Long sumVersionByExecContextId(Long execContextId);
}
