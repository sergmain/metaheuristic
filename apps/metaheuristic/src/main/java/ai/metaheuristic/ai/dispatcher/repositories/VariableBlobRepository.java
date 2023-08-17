/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.beans.VariableBlob;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.Nullable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Blob;

/**
 * @author Sergio Lissner
 * Date: 6/7/2023
 * Time: 2:31 PM
 */
@Profile("dispatcher")
public interface VariableBlobRepository extends CrudRepository<VariableBlob, Long> {

    @Nullable
    @Query(value="select vb.data from VariableBlob vb where vb.id=:id")
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    Blob getDataAsStreamById(Long id);
}
