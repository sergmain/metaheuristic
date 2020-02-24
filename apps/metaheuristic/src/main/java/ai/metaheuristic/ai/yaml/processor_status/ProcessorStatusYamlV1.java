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

package ai.metaheuristic.ai.yaml.processor_status;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.env.EnvYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
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
public class ProcessorStatusYamlV1 implements BaseParams {

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DownloadStatusV1 {
        public Enums.FunctionState functionState;
        public String functionCode;
    }

    public List<DownloadStatusV1> downloadStatuses = new ArrayList<>();

    public EnvYaml env;
    public GitSourcingService.GitStatusInfo gitStatusInfo;
    public String schedule;
    public String sessionId;

    // TODO 2019-05-28, a multi-time-zoned deployment isn't supported right now
    // it'll work but in some cases behaviour can be different
    // need to change to UTC, Coordinated Universal Time
    public long sessionCreatedOn;
    public String ip;
    public String host;

    // contains text of error which can occur while preparing a processor status
    public List<String> errors = null;
    public boolean logDownloadable;
    public int taskParamsVersion;
    public EnumsApi.OS os;

    public final int version = 1;
}
