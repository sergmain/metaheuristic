/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor
@ToString
public class EnvParamsYamlV5 implements BaseParams {

    public final int version=5;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode( of={"code","path"})
    public static class DiskStorageV5 {
        public String code;
        public String path;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode( of={"code"})
    public static class CoreV5 {
        public String code;
        @Nullable
        public String tags;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QuotaV5 {
        public String tag;
        public int amount;
        // string representation of ai.metaheuristic.ai.commons.dispatcher_schedule.DispatcherSchedule
        @Nullable
        public String processingTime;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QuotasV5 {
        public final List<QuotaV5> values = new ArrayList<>();
        public int limit;
        public int defaultValue;
        public boolean disabled;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class VerifyEnvV5 {
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
    public static class EnvV5 {
        public String code;
        public String exec;
        @Nullable
        public VerifyEnvV5 verify;

        public EnvV5(String code, String exec) {
            this.code = code;
            this.exec = exec;
        }
    }

    public final Map<String, String> mirrors = new ConcurrentHashMap<>();
    public final List<EnvV5> envs = new ArrayList<>();
    public final List<DiskStorageV5> disk = new ArrayList<>();
    public final List<CoreV5> cores = new ArrayList<>();
    public final QuotasV5 quotas = new QuotasV5();

}
