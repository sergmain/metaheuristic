/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.beans.ProcessorCore;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author Serge
 * Date: 4/28/2022
 * Time: 11:44 PM
 */
@Repository
@Profile("dispatcher")
public interface ProcessorCoreRepository extends CrudRepository<ProcessorCore, Long> {

    @Override
    @Modifying
    @Query(value="delete from Processor t where t.id=:id")
    void deleteById(Long id);

    Page<Processor> findAll(Pageable pageable);

    @Query(value="select distinct p.processorId from ProcessorCore p")
    List<Long> getAllProcessorIds();

    @Query(value="select c.id from ProcessorCore c where c.processorId=:processorId")
    List<Long> findAllByProcessorId(Pageable pageable, Long processorId);

    @Modifying
    @Query(value="delete from ProcessorCore t where t.id in (:ids)")
    void deleteByIds(List<Long> ids);
}
