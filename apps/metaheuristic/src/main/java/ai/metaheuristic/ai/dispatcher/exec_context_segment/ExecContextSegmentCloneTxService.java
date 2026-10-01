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

package ai.metaheuristic.ai.dispatcher.exec_context_segment;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextJoin;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextSegment;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextJoinRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextSegmentRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.exec_context_segment.ExecContextSegmentParams;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Writes the segment and join records of a cloned ExecContext (041-EXEC-CONTEXT-SEGMENTS-PLAN, Phase 13, decision 15).
 * Each call is its own transaction (decision 3: no transaction spans all segments of an ExecContext): one segment per
 * {@link #copySegment}, one page of join records per {@link #copyJoins}.
 *
 * <p>A copied segment gets a newly allocated id ({@link ExecContextSegmentIdService}), the source's {@code LINE_CTX_ID}
 * (Tasks keep their ctx in a clone), the fork through the Task id map, params through {@link SegmentClone#remap}, and a
 * {@code STRUCTURE_HASH} recomputed from the copied structure. A copied join record keeps its counts and closed flag
 * under the remapped join Task.
 *
 * <p>Error code prefix: {@code 01.920.} (unique to this class).
 */
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ExecContextSegmentCloneTxService {

    private final ExecContextSegmentRepository segmentRepository;
    private final ExecContextJoinRepository joinRepository;
    private final ExecContextSegmentIdService idService;

    /** Copies source segment {@code sourceSegmentId} into ExecContext {@code newExecContextId}; returns the new segment's id. */
    @Transactional
    public Long copySegment(Long sourceSegmentId, Long newExecContextId, Map<Long, Long> taskIdMap, Map<Long, Long> variableIdMap) {
        TxUtils.checkTxExists();
        final ExecContextSegment source = segmentRepository.findById(sourceSegmentId).orElseThrow(
                () -> new IllegalStateException("01.920.010 segment #" + sourceSegmentId + " not found"));
        final ExecContextSegment target = new ExecContextSegment();
        target.id = idService.allocate();
        target.execContextId = newExecContextId;
        target.lineCtxId = source.lineCtxId;
        target.forkTaskId = source.forkTaskId == null ? null : SegmentClone.remapped(source.forkTaskId, taskIdMap);
        target.createdOn = System.currentTimeMillis();
        final ExecContextSegmentParams p = SegmentClone.remap(source.getExecContextSegmentParams(), taskIdMap, variableIdMap, newExecContextId);
        target.updateParams(p);
        target.structureHash = SegmentStructureHash.structureHash(SegmentParamsConverter.segment(target.lineCtxId, target.forkTaskId, p));
        segmentRepository.save(target);
        return target.id;
    }

    /** Copies the join records {@code sourceJoinIds} (one page) into ExecContext {@code newExecContextId}. */
    @Transactional
    public void copyJoins(List<Long> sourceJoinIds, Long newExecContextId, Map<Long, Long> taskIdMap) {
        TxUtils.checkTxExists();
        for (ExecContextJoin source : joinRepository.findAllById(sourceJoinIds)) {
            final ExecContextJoin target = new ExecContextJoin();
            target.execContextId = newExecContextId;
            target.joinTaskId = SegmentClone.remapped(source.joinTaskId, taskIdMap);
            target.linesRegistered = source.linesRegistered;
            target.linesFinished = source.linesFinished;
            target.linesDead = source.linesDead;
            target.closed = source.closed;
            target.createdOn = System.currentTimeMillis();
            joinRepository.save(target);
        }
    }
}
