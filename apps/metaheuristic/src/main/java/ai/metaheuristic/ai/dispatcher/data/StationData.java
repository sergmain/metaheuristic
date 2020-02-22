/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dispatcher.data;

import ai.metaheuristic.ai.dispatcher.beans.Station;
import ai.metaheuristic.api.data.BaseDataClass;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Slice;

public class StationData {

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class StationsResult extends BaseDataClass {
        public Slice<StationStatus> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StationStatus {
        public Station station;
        public boolean active;
        public boolean functionProblem;
        public boolean blacklisted;
        public String blacklistReason;
        public long lastSeen;
        public String ip;
        public String host;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class StationResult extends BaseDataClass {
        public Station station;

        public StationResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public StationResult(Station station) {
            this.station = station;
        }
    }

}
