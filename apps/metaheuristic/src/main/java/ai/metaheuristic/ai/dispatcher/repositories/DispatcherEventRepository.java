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

import ai.metaheuristic.ai.dispatcher.beans.DispatcherEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Serge
 * Date: 10/14/2019
 * Time: 8:20 PM
 */
@Repository
@Transactional
@Profile("dispatcher")
public interface DispatcherEventRepository extends CrudRepository<DispatcherEvent, Long> {

    @Modifying
    void deleteAllByIdIn(List<Long> ids);

    @Override
    @Modifying
    @Query(value="delete from DispatcherEvent t where t.id=:id")
    void deleteById(Long id);

    @Transactional(readOnly = true)
    @Query(value="select e.id from DispatcherEvent e where e.period in :periods")
    List<Long> findIdByPeriod(List<Integer> periods);

    @Transactional(readOnly = true)
    @Query(value="select e from DispatcherEvent e where e.id in :ids ")
    List<DispatcherEvent> findByIds(List<Long> ids);

    @Transactional(readOnly = true)
    @Query(value="select e.period from DispatcherEvent e where e.period < :period ")
    List<Integer> getPeriodsBefore(int period);

    @Transactional(readOnly = true)
    @Query(value="select e.id from DispatcherEvent e where e.period < :period ")
    List<Long> getPeriodIdsBefore(int period);
}
