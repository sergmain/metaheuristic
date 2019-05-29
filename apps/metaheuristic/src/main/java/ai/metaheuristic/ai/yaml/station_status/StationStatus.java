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

package ai.metaheuristic.ai.yaml.station_status;

import ai.metaheuristic.ai.station.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.env.EnvYaml;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class StationStatus {
    public EnvYaml env;
    public GitSourcingService.GitStatusInfo gitStatusInfo;
    public String schedule;
    public String sessionId;
    // TODO 2019-05-28, multi-timezoned deployment isn't supported right now
    // it'll work but in some cases behaviour can be different
    // need to change to UTC, Coordinated Universal Time
    public long sessionCreatedOn;
    public String ip;
    public String host;
    public List<String> errors = null;
    public boolean logDownloadable;

    public void addError(String error) {
        if (errors==null) {
            errors = new ArrayList<>();
        }
        errors.add(error);
    }
}
