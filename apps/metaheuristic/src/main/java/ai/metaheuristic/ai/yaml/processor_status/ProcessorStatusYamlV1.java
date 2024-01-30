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

package ai.metaheuristic.ai.yaml.processor_status;

import ai.metaheuristic.commons.utils.GtiUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.*;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ProcessorStatusYamlV1 implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DownloadStatusV1 {
        public EnumsApi.FunctionState functionState;
        public String functionCode;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LogV1 {
        public boolean logRequested;
        public long requestedOn;

        @Nullable
        public Long logReceivedOn;
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode( of={"code","path"})
    public static class DiskStorageV1 {
        public String code;
        public String path;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnvV1 {
        public final Map<String, String> mirrors = new ConcurrentHashMap<>();
        public final Map<String, String> envs = new ConcurrentHashMap<>();
        public final List<DiskStorageV1> disk = new ArrayList<>();

        @Nullable
        public String tags;

    }

    public List<DownloadStatusV1> downloadStatuses = new ArrayList<>();

    public EnvV1 env;
    public GtiUtils.GitStatusInfo gitStatusInfo;
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

    @Nullable
    public String currDir;

    @Nullable
    public LogV1 log;

    @Nullable
    public String taskIds;
}
