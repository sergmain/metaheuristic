/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
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
    @Query("delete from DispatcherEvent e where e.id in (:ids)")
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

    /** Per EVENT value in [fromPeriod, toPeriod]: the value, the number of events, the first and the last PERIOD. */
    @Transactional(readOnly = true)
    @Query(value="select e.event, count(e), min(e.period), max(e.period) from DispatcherEvent e " +
            "where e.period >= :fromPeriod and e.period <= :toPeriod group by e.event order by e.event")
    List<Object[]> countByEventInPeriods(int fromPeriod, int toPeriod);

    /** id, period, event, companyId and params of the events after afterId in [fromPeriod, toPeriod], in id order. */
    @Transactional(readOnly = true)
    @Query(value="select e.id, e.period, e.event, e.companyId, e.params from DispatcherEvent e " +
            "where e.id > :afterId and e.period >= :fromPeriod and e.period <= :toPeriod order by e.id")
    List<Object[]> findStoredAfter(long afterId, int fromPeriod, int toPeriod, Pageable pageable);

    /** {@link #findStoredAfter}, of the given event types only. */
    @Transactional(readOnly = true)
    @Query(value="select e.id, e.period, e.event, e.companyId, e.params from DispatcherEvent e " +
            "where e.id > :afterId and e.period >= :fromPeriod and e.period <= :toPeriod and e.event in :events order by e.id")
    List<Object[]> findStoredAfterOfEvents(long afterId, int fromPeriod, int toPeriod, Collection<String> events, Pageable pageable);
}
