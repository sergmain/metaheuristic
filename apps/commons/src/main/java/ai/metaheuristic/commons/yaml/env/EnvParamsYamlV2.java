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
public class EnvParamsYamlV2 implements BaseParams {

    public final int version=2;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode( of={"code","path"})
    public static class DiskStorageV2 {
        public String code;
        public String path;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode( of={"code"})
    public static class ProcessorV2 {
        public String code;
        @Nullable
        public String tags;
    }

    public final Map<String, String> mirrors = new ConcurrentHashMap<>();
    public final Map<String, String> envs = new ConcurrentHashMap<>();
    public final List<DiskStorageV2> disk = new ArrayList<>();
    public final List<ProcessorV2> processors = new ArrayList<>();

}
