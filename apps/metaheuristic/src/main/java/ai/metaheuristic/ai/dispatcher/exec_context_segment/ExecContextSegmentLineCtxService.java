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

import ai.metaheuristic.ai.dispatcher.repositories.ExecContextSegmentRepository;
import ai.metaheuristic.commons.utils.ContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code taskContextId} of a new grafted line (041-EXEC-CONTEXT-SEGMENTS-PLAN, Phase 8, decision 10). The siblings of a
 * line at level {@code base} are the segment rows whose {@code LINE_CTX_ID} starts with {@code base#}: every graft line
 * starts its own segment, so no line at a graft base lives inside another segment.
 *
 * <ul>
 *   <li>{@link #nextSequentialLineCtx} - in-band grafts (a splitter's lines, one after another in one transaction) keep
 *       today's numbering {@code base#(max + 1)}, the siblings read from the segments instead of the graph.</li>
 *   <li>{@link #allocateOutOfBand} - out-of-band {@code attachGroup}, the concurrent inserts: no lock. The id of the new
 *       segment is allocated first, the line takes {@code base#(seed + id)}; the seed - the highest sibling index - is read
 *       once per (ExecContext, base) and cached. Ids are unique and positive, so two allocations never meet and every
 *       allocated index lies above every sibling that existed when the seed was read.</li>
 * </ul>
 *
 * <p>An in-band allocation evicts its base's cached seed, so a later out-of-band graft re-reads it above the in-band
 * line. {@code UNIQUE(EXEC_CONTEXT_ID, LINE_CTX_ID)} is the guarantee of last resort.
 */
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ExecContextSegmentLineCtxService {

    /** A new line: the id its segment row takes, and the line ctx derived from it. */
    public record AllocatedLine(Long segmentId, String lineCtxId) {}

    private record SeedKey(Long execContextId, String base) {}

    /** Bound of the seed cache; clearing it costs one sibling read per base on the next out-of-band graft. */
    private static final int MAX_CACHED_SEEDS = 10_000;

    private final ConcurrentHashMap<SeedKey, Long> seeds = new ConcurrentHashMap<>();

    private final ExecContextSegmentRepository segmentRepository;
    private final ExecContextSegmentIdService idService;

    /** In-band: {@code base#(highest sibling index + 1)}, the siblings read from the segments. */
    public String nextSequentialLineCtx(Long execContextId, String base) {
        seeds.remove(new SeedKey(execContextId, base));
        return ContextUtils.nextSiblingTaskContextId(base, siblings(execContextId, base));
    }

    /** Out-of-band, lock-free (decision 10): a fresh segment id and the line ctx {@code base#(seed + id)}. */
    public AllocatedLine allocateOutOfBand(Long execContextId, String base) {
        final Long segmentId = idService.allocate();
        if (seeds.size() >= MAX_CACHED_SEEDS) {
            seeds.clear();
        }
        final long seed = seeds.computeIfAbsent(new SeedKey(execContextId, base),
                k -> SegmentLineCtx.seed(base, siblings(execContextId, base)));
        return new AllocatedLine(segmentId, SegmentLineCtx.lineCtx(base, seed, segmentId));
    }

    private List<String> siblings(Long execContextId, String base) {
        return segmentRepository.findLineCtxIdsByExecContextIdAndLineCtxIdLike(
                execContextId, base + ContextUtils.CONTEXT_SEPARATOR + "%");
    }
}
