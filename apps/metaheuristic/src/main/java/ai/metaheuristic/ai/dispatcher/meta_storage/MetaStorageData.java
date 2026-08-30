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

package ai.metaheuristic.ai.dispatcher.meta_storage;

import org.jspecify.annotations.Nullable;

/**
 * Small-scoped carriers between the orchestrator and the tx service.
 *
 * @author Serge
 */
public class MetaStorageData {

    /**
     * One record as seen by a caller. {@code body} is opaque to MH.
     */
    public record Record(String type, String recKey, String body) {}

    /**
     * A resolved write: the orchestrator has already looked up whether the row exists, so the tx
     * method receives the decision instead of making it.
     *
     * <p>Per SPRING-TX-RULES.md §1, an existence check belongs in the non-transactional orchestrator
     * BEFORE the tx is opened - a {@code @Transactional} method assumes its inputs are validated and
     * touches only what it updates. {@code existingId} is that resolution, carried in at the
     * smallest possible scope.
     */
    public record ResolvedWrite(@Nullable Long existingId, Record record) {}
}
