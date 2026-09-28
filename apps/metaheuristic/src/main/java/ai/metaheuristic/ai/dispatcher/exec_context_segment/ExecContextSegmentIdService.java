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

import ai.metaheuristic.ai.dispatcher.beans.Ids;
import ai.metaheuristic.ai.dispatcher.repositories.IdsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Allocates the id of a new ExecContext segment before the segment is built (041-EXEC-CONTEXT-SEGMENTS-PLAN, decision
 * 10: line index = seed + the segment's id). The number comes from the {@code mh_ids} table generator the same way a
 * Company's unique id does ({@code CompanyTopLevelService.getUniqueId}): an {@link Ids} row is saved to draw the next
 * value and deleted at once, so {@code MH_IDS} keeps no row. Lock-free for the caller - the only shared point is the
 * generator's own row update in {@code mh_gen_ids}.
 *
 * <p>041 Phase 8: allocations are serialized in this JVM. The table generator draws each number over a second, isolated
 * JDBC connection while the {@code Ids} save holds one, and every allocation already queues on the one
 * {@code mh_gen_ids} row. Without this lock, 16 parallel out-of-band grafts took all 10 pooled connections as first
 * connections and each waited for a second: observed {@code Unable to obtain isolated JDBC connection ... active=10,
 * idle=0} after 30 s ({@code SegmentGraftConcurrencyTest}). Waiting on this lock holds no connection, so the queue that
 * the generator row imposes anyway no longer drains the pool.
 */
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ExecContextSegmentIdService {

    private final IdsRepository idsRepository;
    private final ReentrantLock allocationLock = new ReentrantLock();

    public Long allocate() {
        allocationLock.lock();
        try {
            final Long id = idsRepository.save(new Ids()).id;
            idsRepository.deleteById(id);
            return id;
        }
        finally {
            allocationLock.unlock();
        }
    }
}
