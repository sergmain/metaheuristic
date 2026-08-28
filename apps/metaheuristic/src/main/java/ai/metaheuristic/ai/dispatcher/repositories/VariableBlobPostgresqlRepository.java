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

import ai.metaheuristic.ai.dispatcher.beans.VariableBlob;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Serge
 * Date: 12/22/2021
 * Time: 10:22 PM
 */
@Repository
@Profile(value={"dispatcher & postgresql"})
public interface VariableBlobPostgresqlRepository extends VariableBlobDatabaseSpecificRepository<VariableBlob, Long> {

    @Override
    @Modifying
    @Query(nativeQuery = true, value="update mh_variable_blob " +
            "set DATA= (select data from mh_variable_blob where id=:srcId) " +
            "where id=:trgId")
    void copyData(Long srcId, Long trgId);

    /**
     * Deletes a VariableBlob, payload included.
     *
     * <p>On PostgreSQL DATA is an {@code OID} - a 4-byte pointer into {@code pg_largeobject}, not the
     * bytes themselves - and PostgreSQL enforces no referential integrity between an oid column and the
     * large-object catalog. A plain DELETE therefore drops the pointer and strands the object: it stays
     * allocated, and with the row gone nothing can address it again. Hibernate never issues an unlink,
     * because to the ORM the column value simply went away with the row.
     *
     * <p>{@code lo_unlink} sits in the row filter rather than in a statement of its own, which is what
     * makes the ordering structural instead of a matter of planner choice: the tuple cannot be removed
     * unless the qual passed, so the object is always freed first. The {@code data is null} arm keeps
     * the row deletable if the column ever loses its NOT NULL.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying
    @Query(nativeQuery = true, value="delete from mh_variable_blob b " +
            "where b.id=:id and (b.data is null or lo_unlink(b.data) is not null)")
    void delete(Long id);

}
