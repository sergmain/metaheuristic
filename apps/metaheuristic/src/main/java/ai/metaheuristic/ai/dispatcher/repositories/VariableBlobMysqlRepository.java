/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.beans.Variable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

/**
 * @author Serge
 * Date: 12/22/2021
 * Time: 10:22 PM
 */
@Repository
@Profile(value={"dispatcher & mysql"})
public interface VariableBlobMysqlRepository extends VariableBlobDatabaseSpecificRepository<Variable, Long> {

    @Override
    @Modifying
    @Query(nativeQuery = true, value="update mh_variable as trg, (select data from mh_cache_variable where id=:srcId) as src " +
            "set trg.DATA= src.data, trg.FILENAME=:filename, trg.IS_INITED=true, trg.IS_NULLIFIED=false, trg.UPLOAD_TS=:uploadedOn " +
            "where trg.id=:trgId")
    void copyData(Long srcId, Long trgId, @Nullable String filename, Timestamp uploadedOn);


}
