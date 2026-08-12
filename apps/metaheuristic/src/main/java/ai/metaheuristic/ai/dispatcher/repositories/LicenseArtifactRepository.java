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

import ai.metaheuristic.ai.dispatcher.beans.LicenseArtifact;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Serge
 */
@Repository
@Transactional
@Profile("dispatcher")
public interface LicenseArtifactRepository extends CrudRepository<LicenseArtifact, Long> {

    /** Every live license, oldest first, so the admin breakdown has a stable order. */
    @Transactional(readOnly = true)
    @Query(value = "select a from LicenseArtifact a where a.deleted=false order by a.id asc")
    List<LicenseArtifact> findAllLive();

    /** Including removed ones — a re-install of a previously removed license must find its row. */
    @Transactional(readOnly = true)
    @Nullable
    LicenseArtifact findByTokenHash(String tokenHash);
}
