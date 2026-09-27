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

/**
 * Allocates the id of a new ExecContext segment before the segment is built (041-EXEC-CONTEXT-SEGMENTS-PLAN, decision
 * 10: line index = seed + the segment's id). The number comes from the {@code mh_ids} table generator the same way a
 * Company's unique id does ({@code CompanyTopLevelService.getUniqueId}): an {@link Ids} row is saved to draw the next
 * value and deleted at once, so {@code MH_IDS} keeps no row. Lock-free for the caller - the only shared point is the
 * generator's own row update in {@code mh_gen_ids}.
 */
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ExecContextSegmentIdService {

    private final IdsRepository idsRepository;

    public Long allocate() {
        final Long id = idsRepository.save(new Ids()).id;
        idsRepository.deleteById(id);
        return id;
    }
}
