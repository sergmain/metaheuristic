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

package ai.metaheuristic.ai.yaml;

import ai.metaheuristic.ai.yaml.station_status.StationStatus;
import ai.metaheuristic.ai.yaml.station_status.StationStatusUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Serge
 * Date: 5/23/2019
 * Time: 3:54 PM
 */
public class TestStationStatus {

    @Test
    public void test() throws IOException {
        try (InputStream is = TestStationStatus.class.getResourceAsStream("/yaml/station/station-status-01.yaml")) {
            StationStatus ss = StationStatusUtils.to(is);
        }
    }
}
