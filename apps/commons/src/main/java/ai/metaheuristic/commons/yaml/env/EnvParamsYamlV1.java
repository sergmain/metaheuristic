/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
@AllArgsConstructor
public class EnvParamsYamlV1 implements BaseParams {

    public final int version=1;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode( of={"code","path"})
    public static class DiskStorageV1 {
        public String code;
        public String path;
    }

    public final Map<String, String> mirrors = new ConcurrentHashMap<>();
    public final Map<String, String> envs = new ConcurrentHashMap<>();
    public final List<DiskStorageV1> disk = new ArrayList<>();

    @Nullable
    public String tags;

}
