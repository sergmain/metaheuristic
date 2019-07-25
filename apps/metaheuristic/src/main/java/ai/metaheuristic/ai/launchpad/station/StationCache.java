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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.launchpad.beans.Station;
import ai.metaheuristic.ai.launchpad.repositories.StationsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
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

    @CacheEvict(cacheNames = {Consts.STATIONS_CACHE}, allEntries = true)
    public void clearCache() {
    }

    public StationCache(StationsRepository stationsRepository) {
        this.stationsRepository = stationsRepository;
    }

    @CacheEvict(cacheNames = {Consts.STATIONS_CACHE}, key = "#result.id")
    public Station save(Station station) {
        if (station==null) {
            return null;
        }
        log.debug("#457.010 save station, id: #{}, station: {}", station.id, station);
        return stationsRepository.save(station);
    }

    @CacheEvict(cacheNames = {Consts.STATIONS_CACHE}, key = "#station.id")
    public void delete(Station station) {
        if (station==null || station.id==null) {
            return;
        }
        try {
            stationsRepository.delete(station);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#457.030 Error deleting of station by object", e);
        }
    }

    @CacheEvict(cacheNames = {Consts.STATIONS_CACHE}, key = "#id")
    public void evictById(Long id) {
        //
    }

    @CacheEvict(cacheNames = {Consts.STATIONS_CACHE}, key = "#stationId")
    public void delete(Long stationId) {
        if (stationId==null) {
            return;
        }
        try {
            stationsRepository.deleteById(stationId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#457.050 Error deleting of station by id", e);
        }
    }

    @CacheEvict(cacheNames = {Consts.STATIONS_CACHE}, key = "#stationId")
    public void deleteById(Long stationId) {
        if (stationId==null) {
            return;
        }
        try {
            stationsRepository.deleteById(stationId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#457.070 Error deleting of station by id", e);
        }
    }

    @Cacheable(cacheNames = {Consts.STATIONS_CACHE}, unless="#result==null")
    public Station findById(Long id) {
        return stationsRepository.findById(id).orElse(null);
    }
}
