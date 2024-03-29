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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ProcessorStatusYamlV3 implements BaseParams {

    public final int version=3;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LogV3 {
        public boolean logRequested;
        public long requestedOn;

        @Nullable
        public Long logReceivedOn;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode( of={"code","path"})
    public static class DiskStorageV3 {
        public String code;
        public String path;
    }

    // event though at processor side a quatas is placed in env.yaml above all processors level
    // here it'll be placed at concrete processor
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QuotaV3 {
        public String tag;
        public int amount;
        // processor can disable specific tag. i.e. on scheduler basis
        public boolean disabled;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QuotasV3 {
        public List<QuotaV3> values = new ArrayList<>();
        public int limit;
        public int defaultValue;
        public boolean disabled;
    }

    @Data
    @NoArgsConstructor
    public static class EnvV3 {
        public final Map<String, String> mirrors = new ConcurrentHashMap<>();
        public final Map<String, String> envs = new ConcurrentHashMap<>();
        public final List<DiskStorageV3> disk = new ArrayList<>();
        public final QuotasV3 quotas = new QuotasV3();
    }

    @Nullable
    public EnvV3 env;
    public GtiUtils.GitStatusInfo gitStatusInfo;
    public String schedule;
    public String sessionId;

    // TODO 2019-05-28, a multi-time-zoned deployment isn't supported right now
    //  it'll work but in some cases behaviour can be different
    //  need to change to UTC, Coordinated Universal Time
    // TODO 2020-10-11 actually, it's working in prod with multi-time-zoned.
    //  So need to decide about implementing the support of UTC
    // TODO 2022-04-30 actually, it isn't working correctly in multi-timezone environment
    //  tasks have been loosing periodically
    public long sessionCreatedOn;
    public String ip;
    public String host;

    // contains text of error which can occur while preparing a processor status
    @Nullable
    public List<String> errors = null;
    public boolean logDownloadable;
    public int taskParamsVersion;
    public EnumsApi.OS os;

    public String currDir;

    @Nullable
    public LogV3 log;

    @Deprecated(forRemoval = true)
    // key - code of function, value - stae of function
    private Map<String, EnumsApi.FunctionState> functions = new HashMap<>();

    public Map<String, EnumsApi.FunctionState> getFunctions() {
        return functions;
    }

    public void setFunctions(Map<String, EnumsApi.FunctionState> functions) {
        this.functions = functions;
    }

    public void addError(String error) {
        if (errors==null) {
            errors = new ArrayList<>();
        }
        errors.add(error);
    }
}
