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

    /** Segments whose start line forks from {@code forkTaskId} (index {@code mh_exec_context_segment_ec_fork_idx}). */
    @Query(value="select s from ExecContextSegment s where s.execContextId=:execContextId and s.forkTaskId=:forkTaskId")
    List<ExecContextSegment> findByExecContextIdAndForkTaskId(Long execContextId, Long forkTaskId);

    /** Number of segments of an ExecContext (041 Phase 12: part of the change-detection version). */
    @Query(value="select count(s.id) from ExecContextSegment s where s.execContextId=:execContextId")
    long countByExecContextId(Long execContextId);

    /** Sum of the segments' {@code VERSION}s, null without segments - grows on every segment write (041 Phase 12). */
    @Nullable
    @Query(value="select sum(s.version) from ExecContextSegment s where s.execContextId=:execContextId")
    Long sumVersionByExecContextId(Long execContextId);

    /** One page of segment ids of an ExecContext, by id (041 Phase 14: bounded deletion). */
    @Query(value="select s.id from ExecContextSegment s where s.execContextId=:execContextId order by s.id")
    List<Long> findIdsPageByExecContextId(org.springframework.data.domain.Pageable pageable, Long execContextId);

    /** Every ExecContext id that has a segment (041 Phase 14: finding the segments of deleted ExecContexts). */
    @Query(value="select distinct s.execContextId from ExecContextSegment s")
    List<Long> findAllExecContextIds();

    @org.springframework.data.jpa.repository.Modifying
    @Query(value="delete from ExecContextSegment s where s.id in (:ids)")
    void deleteByIds(List<Long> ids);

    /** {@code (LINE_CTX_ID, STRUCTURE_HASH)} of every segment of an ExecContext, no params (041 Phase 15: what sealing reads). */
    @Query(value="select s.lineCtxId, s.structureHash from ExecContextSegment s where s.execContextId=:execContextId")
    List<Object[]> findStructureHashesByExecContextId(Long execContextId);
}
