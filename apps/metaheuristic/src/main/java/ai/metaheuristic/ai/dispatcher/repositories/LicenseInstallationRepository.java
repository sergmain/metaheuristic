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

import ai.metaheuristic.ai.dispatcher.beans.LicenseInstallation;
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
public interface LicenseInstallationRepository extends CrudRepository<LicenseInstallation, Long> {

    /**
     * Every row, oldest first. There should be exactly one; the query returns a list anyway so a
     * violated invariant can be resolved deterministically instead of throwing at boot and
     * leaving the dispatcher unable to start (see LicenseInstallationUtils#pickAuthoritative).
     */
    @Transactional(readOnly = true)
    @Query(value = "select i from LicenseInstallation i order by i.id asc")
    List<LicenseInstallation> findAllOrderByIdAsc();
}
