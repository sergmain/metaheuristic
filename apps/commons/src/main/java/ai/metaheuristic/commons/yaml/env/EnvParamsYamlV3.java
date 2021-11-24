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
public class EnvParamsYamlV3 implements BaseParams {

    public final int version=3;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode( of={"code","path"})
    public static class DiskStorageV3 {
        public String code;
        public String path;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode( of={"code"})
    public static class ProcessorV3 {
        public String code;
        @Nullable
        public String tags;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QuotaV3 {
        public String tag;
        public int amount;
        // string representation of ai.metaheuristic.ai.commons.dispatcher_schedule.DispatcherSchedule
        public String processingTime;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QuotasV3 {
        public final List<QuotaV3> values = new ArrayList<>();
        public int limit;
        public int defaultValue;
        public boolean disabled;
    }

    public final Map<String, String> mirrors = new ConcurrentHashMap<>();
    public final Map<String, String> envs = new ConcurrentHashMap<>();
    public final List<DiskStorageV3> disk = new ArrayList<>();
    public final List<ProcessorV3> processors = new ArrayList<>();
    public final QuotasV3 quotas = new QuotasV3();

}
