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
import ai.metaheuristic.ai.dispatcher.beans.ExecContextVariableState;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author Serge
 * Date: 3/20/2021
 * Time: 12:05 AM
 */
@Repository
@Profile("dispatcher")
public interface ExecContextVariableStateRepository extends CrudRepository<ExecContextVariableState, Long> {

    @Query(value="select distinct v.execContextId from ExecContextVariableState v")
    List<Long> getAllExecContextIds();

    @Query(value="select v.id from ExecContextVariableState v where v.execContextId=:execContextId")
    List<Long> findAllByExecContextId(Pageable pageable, Long execContextId);

    void deleteAllByIdIn(List<Long> ids);

}
