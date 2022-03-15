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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Serge
 * Date: 3/17/2021
 * Time: 11:04 AM
 */
@Repository
@Profile("dispatcher")
public interface ExecContextTaskStateRepository extends CrudRepository<ExecContextTaskState, Long> {

    @Query(value="select distinct v.execContextId from ExecContextTaskState v")
    List<Long> getAllExecContextIds();

    @Query(value="select v.id from ExecContextTaskState v where v.execContextId=:execContextId")
    List<Long> findAllByExecContextId(Pageable pageable, Long execContextId);

    @Modifying
    void deleteAllByIdIn(List<Long> ids);

    @Query(value="select w.id from ExecContextTaskState w")
    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    List<Long> findAllIds();
}
