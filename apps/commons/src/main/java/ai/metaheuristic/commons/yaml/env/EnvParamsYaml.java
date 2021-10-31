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
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import lombok.*;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor
@ToString
public class EnvParamsYaml implements BaseParams {

    public final int version=3;

    public boolean checkIntegrity() {
        if (!quotas.disabled && (quotas.defaultValue==0)) {
            throw new CheckIntegrityFailedException("(!quotas.disabled && (quotas.defaultValue==0))");
        }
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode( of={"code","path"})
    public static class DiskStorage {
        public String code;
        public String path;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode( of={"code"})
    public static class Processor {
        public String code;
        @Nullable
        public String tags;
    }

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

    public final Map<String, String> mirrors = new ConcurrentHashMap<>();
    public final Map<String, String> envs = new ConcurrentHashMap<>();
    public final List<DiskStorage> disk = new ArrayList<>();
    public final List<Processor> processors = new ArrayList<>();
    public final Quotas quotas = new Quotas();

}
