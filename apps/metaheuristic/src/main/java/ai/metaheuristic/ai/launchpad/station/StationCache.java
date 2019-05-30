/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.station;

import ai.metaheuristic.ai.launchpad.beans.Station;
import ai.metaheuristic.ai.launchpad.repositories.StationsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 5/29/2019
 * Time: 7:25 PM
 */

@Service
@Profile("launchpad")
@Slf4j
public class StationCache {

    private final StationsRepository stationsRepository;

    public StationCache(StationsRepository stationsRepository) {
        this.stationsRepository = stationsRepository;
    }

    @CacheEvict(cacheNames = "stations", key = "#result.id")
    public Station save(Station station) {
        return stationsRepository.save(station);
    }

    @CacheEvict(cacheNames = {"stations"}, key = "#station.id")
    public void delete(Station station) {
        try {
            stationsRepository.delete(station);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error deleting of snippet by object", e);
        }
    }

    @CacheEvict(cacheNames = {"stations"}, key = "#stationId")
    public void delete(Long stationId) {
        try {
            stationsRepository.deleteById(stationId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error deleting of station by id", e);
        }
    }

    @CacheEvict(cacheNames = {"stations"}, key = "#stationId")
    public void deleteById(Long stationId) {
        try {
            stationsRepository.deleteById(stationId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error deleting of station by id", e);
        }
    }

    @Cacheable(cacheNames = "stations", unless="#result==null")
    public Station findById(Long id) {
        return stationsRepository.findById(id).orElse(null);
    }
}