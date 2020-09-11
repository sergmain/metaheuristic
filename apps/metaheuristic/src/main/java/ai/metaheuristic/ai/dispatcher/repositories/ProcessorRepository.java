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

import ai.metaheuristic.ai.dispatcher.beans.Processor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * User: Serg
 * Date: 25.06.2017
 * Time: 15:52
 */
@Repository
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Profile("dispatcher")
public interface ProcessorRepository extends CrudRepository<Processor, Long> {

    @NonNull
    Optional<Processor> findById(Long id);

    @Nullable
    @Query(value="select s from Processor s where s.id=:id")
    Processor findByIdForUpdate(Long id);

    Page<Processor> findAll(Pageable pageable);

    @Query(value="select s.id from Processor s order by s.updatedOn desc")
    Slice<Long> findAllByOrderByUpdatedOnDescId(Pageable pageable);

}