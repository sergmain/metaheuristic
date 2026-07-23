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

package ai.metaheuristic.commons.yaml.function;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.*;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 11/3/2019
 * Time: 4:53 PM
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class FunctionConfigYaml implements BaseParams, Cloneable {

    public final int version=3;

    @Override
    public boolean checkIntegrity() {
        if (function.sourcing==null) {
            throw new CheckIntegrityFailedException("sourcing==null");
        }
        List<String> errors = new ArrayList<>();
        if (function.sourcing==EnumsApi.FunctionSourcing.dispatcher) {
            if (function.targets==null || function.targets.isEmpty()) {
                errors.add(S.f("function %s has a sourcing as %s but targets are empty", function.code, function.sourcing));
            }
            else {
                for (Map.Entry<String, Target> e : function.targets.entrySet()) {
                    if (S.b(e.getValue().file)) {
                        errors.add(S.f("function %s, target '%s' has an empty file", function.code, e.getKey()));
                    }
                }
            }
        }
        final String value = MetaUtils.getValue(function.metas, ConstsApi.META_MH_TASK_PARAMS_VERSION);
        if (value!=null) {
            int ver = Integer.parseInt(value);
            if (ver!=1 && ver!=2) {
                errors.add(S.f("function %s has unsupported version, version==%s, as value of 'mh.task-params-version'", function.code, value));
            }
        }
        if (function.metas!=null) {
            for (Map<String, String> meta : function.metas) {
                if (meta.size()!=1) {
                    errors.add(S.f("function %s has an incorrectly defined meta, must be one meta per yaml element, %s", function.code, meta));
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new CheckIntegrityFailedException(errors.toString());
        }

        return true;
    }

    @SneakyThrows
    @Override
    public FunctionConfigYaml clone() {
        FunctionConfigYaml clone = (FunctionConfigYaml) super.clone();
        clone.function = this.function.clone();
        clone.system = this.system.clone();
//        clone.system.archive = this.system.archive;
//        clone.system.checksumMap.putAll(this.system.checksumMap);
        return clone;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class System implements Cloneable {
        public Map<EnumsApi.HashAlgo, String> checksumMap = new HashMap<>();
        public String archive;

        @SneakyThrows
        public System clone() {
            final System clone = (System) super.clone();
            clone.checksumMap = new HashMap<>(this.checksumMap);
            clone.archive = this.archive;
            return clone;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Api {
        public String keyCode;
    }

    /**
     * A per-OS deployment target inside the function package. 'src' is the subdirectory
     * holding the actual file (default {@link CommonConsts#DEFAULT_FUNCTION_SRC_DIR});
     * 'file' is the executable/artifact filename within that subdirectory. One Target per
     * supported OS/arch, keyed in {@link FunctionConfig#targets} by an OsArch key
     * (e.g. linux_amd64) or by {@link CommonConsts#MH_DEFAULT_OS_KEY} for OS-agnostic.
     */
    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Target implements Cloneable {
        public String src = CommonConsts.DEFAULT_FUNCTION_SRC_DIR;
        public @Nullable String file;

        @SneakyThrows
        public Target clone() {
            return (Target) super.clone();
        }
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(of = "code")
    public static class FunctionConfig implements Cloneable {

        @SneakyThrows
        public FunctionConfig clone() {
            final FunctionConfig clone = (FunctionConfig) super.clone();
            if (this.metas!=null) {
                clone.metas = new ArrayList<>(this.metas);
            }
            clone.targets = new LinkedHashMap<>();
            for (Map.Entry<String, Target> e : this.targets.entrySet()) {
                clone.targets.put(e.getKey(), e.getValue().clone());
            }
            return clone;
        }

        /**
         * code of function, i.e. simple-app:1.0
         */
        public String code;
        public @Nullable String type;
        /**
         * params for command line for invoking function
         * <p>
         * this isn't a holder for yaml-based config
         */
        public @Nullable String params;

        public @Nullable String env;
        public EnumsApi.@Nullable FunctionSourcing sourcing;
        public @Nullable GitInfo git;
        public @Nullable List<Map<String, String>> metas = new ArrayList<>();

        /**
         * per-OS deployment targets: key is an OsArch key (e.g. linux_amd64) or
         * {@link CommonConsts#MH_DEFAULT_OS_KEY}. Replaces the former single src+file pair.
         */
        public @Nullable Map<String, Target> targets = new LinkedHashMap<>();

        public @Nullable String assetDir;

        /**
         * Processor-side cleaning policy, @Nullable per the @Nullable-exception rule of
         * the multi-versioning mechanic - no version bump. null means 'nothing to clean
         * because of this Function', the SourceCode's 'clean' option still applies.
         */
        public EnumsApi.@Nullable CleaningPolicy cleaningPolicy;

        public FunctionConfigYaml.@Nullable Api api = null;
    }

    public FunctionConfig function = new FunctionConfig();

    public @Nullable System system = new System();

}
