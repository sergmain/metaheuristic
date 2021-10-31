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

package ai.metaheuristic.ai.yaml.processor_status;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
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
public class ProcessorStatusYamlV2 implements BaseParams {

    public final int version = 2;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DownloadStatusV2 {
        public Enums.FunctionState functionState;
        public String functionCode;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LogV2 {
        public boolean logRequested;
        public long requestedOn;

        @Nullable
        public Long logReceivedOn;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode( of={"code","path"})
    public static class DiskStorageV2 {
        public String code;
        public String path;
    }

    // event though at processor side a quatas is placed in env.yaml above all processors level
    // here it'll be placed at concrete processor
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QuotaV2 {
        public String tag;
        public int amount;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QuotasV2 {
        public List<QuotaV2> values = new ArrayList<>();
        public int limit;
        public int defaultValue;
        public boolean disabled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnvV2 {
        public final Map<String, String> mirrors = new ConcurrentHashMap<>();
        public final Map<String, String> envs = new ConcurrentHashMap<>();
        public final List<DiskStorageV2> disk = new ArrayList<>();

        @Nullable
        public String tags;

        public final QuotasV2 quotas = new QuotasV2();
    }

    public List<DownloadStatusV2> downloadStatuses = new ArrayList<>();

    public EnvV2 env;
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

    @Nullable
    public String currDir;

    @Nullable
    public LogV2 log;

    @Nullable
    public String taskIds;
}
