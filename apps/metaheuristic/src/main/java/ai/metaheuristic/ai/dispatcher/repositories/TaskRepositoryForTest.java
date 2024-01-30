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

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Serge
 * Date: 4/9/2021
 * Time: 5:35 PM
 */
@Repository
@Profile("dispatcher")
public interface TaskRepositoryForTest extends CrudRepository<TaskImpl, Long> {

    @Query(value="select t from TaskImpl t where t.coreId=:coreId and t.resultReceived=0")
    List<TaskImpl> findByCoreIdAndResultReceivedIs0(Long coreId);

    @Query(value="select t.id, t.execState, t.updatedOn, t.params from TaskImpl t where t.execContextId=:execContextId")
    List<Object[]> findAllExecStateAndParamsByExecContextId(Long execContextId);

    @Query(value="select t.id, t.params from TaskImpl t where t.execContextId=:execContextId")
    List<Object[]> findByExecContextId(Long execContextId);

    @Query(value="select t from TaskImpl t where t.execContextId=:execContextId")
    List<TaskImpl> findByExecContextIdAsList(Long execContextId);

    @Modifying
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    void deleteByExecContextId(Long execContextId);

}
