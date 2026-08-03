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

import ai.metaheuristic.ai.dispatcher.beans.Invite;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 8/2/2026
 */
@Repository
@Transactional
@Profile("dispatcher")
public interface InviteRepository extends CrudRepository<Invite, Long> {

    @Transactional(readOnly = true)
    @Nullable
    Invite findByToken(String token);

    /**
     * Pessimistic read for the redemption path: redemption must not race with
     * itself, or one token mints two accounts. The service takes this row
     * inside the same transaction that writes {@code INVITED_ACCOUNT_ID}.
     */
    @Transactional
    @Nullable
    @Query(value = "select i from Invite i where i.token=:token")
    Invite findByTokenForUpdate(String token);

    @Transactional(readOnly = true)
    List<Invite> findAllByCompanyId(Long companyId);
}
