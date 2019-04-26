/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.repositories;

import aiai.ai.launchpad.beans.FlowInstance;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

@Repository
@Profile("launchpad")
@Transactional
public interface FlowInstanceRepository extends CrudRepository<FlowInstance, Long> {

    @Transactional(readOnly = true)
    @Query(value="select f from FlowInstance f")
    Stream<FlowInstance> findAllAsStream();

    @Transactional(readOnly = true)
    Slice<FlowInstance> findAllByOrderByExecStateDescCompletedOnDesc(Pageable pageable);


    List<FlowInstance> findByExecStateOrderByCreatedOnAsc(int execSate);

    List<FlowInstance> findByExecState(int execState);

    Slice<FlowInstance> findByFlowId(Pageable pageable, long flowId);

    Slice<FlowInstance> findByFlowIdOrderByCreatedOnDesc(Pageable pageable, long flowId);

    List<FlowInstance> findByFlowId(long flowId);
}

