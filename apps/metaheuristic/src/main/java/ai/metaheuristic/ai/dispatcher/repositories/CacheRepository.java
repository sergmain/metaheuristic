/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

import ai.metaheuristic.ai.dispatcher.beans.Cache;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Blob;

/**
 * @author Serge
 * Date: 10/7/2020
 * Time: 9:29 PM
 */
@Repository
@Transactional
@Profile("dispatcher")
public interface CacheRepository extends CrudRepository<Cache, Long> {

    @Query(value="select b.data from Cache b where b.id=:id")
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    Blob getDataAsStreamById(Long id);

    @Query(value="select b.data from Cache b where b.keySha256Length=:key")
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    Blob getDataAsStreamByKey(String key);

}
