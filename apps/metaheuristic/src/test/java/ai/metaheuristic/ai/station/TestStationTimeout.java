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

package ai.metaheuristic.ai.station;

import ai.metaheuristic.ai.mh.dispatcher..server.ServerService;
import ai.metaheuristic.ai.mh.dispatcher..station.StationTopLevelService;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/**
 * @author Serge
 * Date: 8/3/2019
 * Time: 12:02 AM
 */
public class TestStationTimeout {

    @Test
    public void test() {
        assertTrue(
                StationTopLevelService.STATION_TIMEOUT - ServerService.SESSION_UPDATE_TIMEOUT
                        >= TimeUnit.SECONDS.toMillis(20)
        );
    }
}
