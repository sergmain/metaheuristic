/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.beans.Processor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * User: Serg
 * Date: 25.06.2017
 * Time: 15:52
 */
@Repository
@Profile("dispatcher")
public interface ProcessorRepository extends CrudRepository<Processor, Long> {

    @Override
    @Modifying
    @Query(value="delete from Processor t where t.id=:id")
    void deleteById(Long id);

    Page<Processor> findAll(Pageable pageable);

    @Query(value="select s.id from Processor s order by s.updatedOn desc")
    Slice<Long> findAllByOrderByUpdatedOnDescId(Pageable pageable);

    @Query(value="select p.id from Processor p")
    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    List<Long> findAllIds();
}