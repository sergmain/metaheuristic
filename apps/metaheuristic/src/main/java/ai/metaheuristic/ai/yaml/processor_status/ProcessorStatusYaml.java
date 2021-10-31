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
public class ProcessorStatusYaml implements BaseParams {

    public final int version=2;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DownloadStatus {
        public Enums.FunctionState functionState;
        public String functionCode;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Log {
        public boolean logRequested;
        public long requestedOn;

        @Nullable
        public Long logReceivedOn;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode( of={"code","path"})
    public static class DiskStorage {
        public String code;
        public String path;
    }

    // event though at processor side a quatas is placed in env.yaml above all processors level
    // here it'll be placed at concrete processor
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Quota {
        public String tag;
        public int amount;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Quotas {
        public List<Quota> values = new ArrayList<>();
        public int limit;
        public int defaultValue;
        public boolean disabled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Env {
        public final Map<String, String> mirrors = new ConcurrentHashMap<>();
        public final Map<String, String> envs = new ConcurrentHashMap<>();
        public final List<DiskStorage> disk = new ArrayList<>();

        @Nullable
        public String tags;

        public final Quotas quotas = new Quotas();
    }

    public List<DownloadStatus> downloadStatuses = new ArrayList<>();

    @Nullable
    public Env env;
    public GitSourcingService.GitStatusInfo gitStatusInfo;
    public String schedule;
    public String sessionId;

    // TODO 2019-05-28, a multi-time-zoned deployment isn't supported right now
    //  it'll work but in some cases behaviour can be different
    //  need to change to UTC, Coordinated Universal Time
    // TODO 2020-10-11 actually, it's working in prod with multi-time-zoned.
    //  So need to decide about implementing the support of UTC
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
    public Log log;

    @Nullable
    public String taskIds;

    public void addError(String error) {
        if (errors==null) {
            errors = new ArrayList<>();
        }
        errors.add(error);
    }
}
