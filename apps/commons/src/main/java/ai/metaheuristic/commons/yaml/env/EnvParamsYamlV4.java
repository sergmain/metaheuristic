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
package ai.metaheuristic.commons.yaml.env;

import ai.metaheuristic.api.data.BaseParams;
import lombok.*;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor
@ToString
public class EnvParamsYamlV4 implements BaseParams {

    public final int version=4;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode( of={"code","path"})
    public static class DiskStorageV4 {
        public String code;
        public String path;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode( of={"code"})
    public static class ProcessorV4 {
        public String code;
        @Nullable
        public String tags;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QuotaV4 {
        public String tag;
        public int amount;
        // string representation of ai.metaheuristic.ai.commons.dispatcher_schedule.DispatcherSchedule
        @Nullable
        public String processingTime;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QuotasV4 {
        public final List<QuotaV4> values = new ArrayList<>();
        public int limit;
        public int defaultValue;
        public boolean disabled;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class VerifyEnvV4 {
        public boolean run = false;
        @Nullable
        public String params;
        @Nullable
        public String responsePattern;
        @Nullable
        public String expectedResponse;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EnvV4 {
        public String code;
        public String exec;
        @Nullable
        public VerifyEnvV4 verify;

        public EnvV4(String code, String exec) {
            this.code = code;
            this.exec = exec;
        }
    }

    public final Map<String, String> mirrors = new ConcurrentHashMap<>();
    public final List<EnvV4> envs = new ArrayList<>();
    public final List<DiskStorageV4> disk = new ArrayList<>();
    public final List<ProcessorV4> processors = new ArrayList<>();
    public final QuotasV4 quotas = new QuotasV4();

}
