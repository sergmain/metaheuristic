/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.beans.ExecutionGate;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The only place a read of MH_EXECUTION_GATE is declared. Callers on the hot path do not come here
 * at all — they read the in-memory copy — so every query below serves either the startup load or a
 * write that has to load the row it is about to change.
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@Repository
@Transactional
@Profile("dispatcher")
public interface ExecutionGateRepository extends CrudRepository<ExecutionGate, Long> {

    /** The row covering a key, if there is one. Unique index guarantees at most one. */
    @Transactional(readOnly = true)
    @Query(value = "select g from ExecutionGate g where g.scope=:scope and g.refKey=:refKey")
    @Nullable
    ExecutionGate findByScopeAndRefKey(String scope, String refKey);

    /** Everything still live at the given instant — the startup load. */
    @Transactional(readOnly = true)
    @Query(value = "select g from ExecutionGate g where g.blockedUntil>:now")
    List<ExecutionGate> findAllLive(long now);

    /** Ids of everything whose deadline has passed — the expiry sweep. */
    @Transactional(readOnly = true)
    @Query(value = "select g.id from ExecutionGate g where g.blockedUntil<=:now")
    List<Long> findExpiredIds(long now);
}
