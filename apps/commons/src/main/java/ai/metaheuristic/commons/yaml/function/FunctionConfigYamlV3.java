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

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.sourcing.DiskInfo;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import lombok.*;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Frozen snapshot of the V3 schema (per-OS 'targets'). Mirrors the version-less
 * FunctionConfigYaml at the time V3 became the latest. Never modify its field set.
 *
 * @author Serge
 * Date: 11/3/2019
 * Time: 4:53 PM
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class FunctionConfigYamlV3 implements BaseParams, Cloneable {

    public final int version=3;

    @Override
    public boolean checkIntegrity() {
        if (function.sourcing==null) {
            throw new CheckIntegrityFailedException("sourcing==null");
        }
        return true;
    }

    @SneakyThrows
    @Override
    public FunctionConfigYamlV3 clone() {
        FunctionConfigYamlV3 clone = (FunctionConfigYamlV3) super.clone();
        clone.function = this.function.clone();
        if (this.system!=null) {
            clone.system = this.system.clone();
        }
        if (this.dataStorage!=null) {
            clone.dataStorage = this.dataStorage.clone();
        }
        return clone;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemV3 implements Cloneable {
        public Map<EnumsApi.HashAlgo, String> checksumMap = new HashMap<>();
        public String archive;

        @SneakyThrows
        public SystemV3 clone() {
            final SystemV3 clone = (SystemV3) super.clone();
            clone.checksumMap = new HashMap<>(this.checksumMap);
            clone.archive = this.archive;
            return clone;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataStorageV3 implements Cloneable {

        // it's a name of asset. Asset can be Variable, GlobalVariable or Function
        // for Variable and GlobalVariable it's a 'name' field
        // for Function it's a 'code' field
        public String name;

        public EnumsApi.DataSourcing sourcing;

        public @Nullable GitInfo git;

        public @Nullable DiskInfo disk;

        public EnumsApi.@Nullable VariableType type;

        public @Nullable Long size = null;

        public @Nullable Map<EnumsApi.HashAlgo, String> checksumMap = null;

        @SneakyThrows
        public DataStorageV3 clone() {
            final DataStorageV3 clone = (DataStorageV3) super.clone();
            if (this.checksumMap!=null) {
                clone.checksumMap = new HashMap<>(this.checksumMap);
            }
            return clone;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiV3 {
        public String keyCode;
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TargetV3 implements Cloneable {
        public String src = CommonConsts.DEFAULT_FUNCTION_SRC_DIR;
        public @Nullable String file;

        @SneakyThrows
        public TargetV3 clone() {
            return (TargetV3) super.clone();
        }
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyzerV3 {
        public String name;
        public @Nullable List<String> regex = new ArrayList<>();
        public String timeout;
        public boolean incrementTries;
        public EnumsApi.GateScope scope;
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(of = "code")
    public static class FunctionConfigV3 implements Cloneable {

        @SneakyThrows
        public FunctionConfigV3 clone() {
            final FunctionConfigV3 clone = (FunctionConfigV3) super.clone();
            if (this.metas!=null) {
                clone.metas = new ArrayList<>(this.metas);
            }
            if (this.targets!=null) {
                clone.targets = new LinkedHashMap<>();
                for (Map.Entry<String, TargetV3> e : this.targets.entrySet()) {
                    clone.targets.put(e.getKey(), e.getValue().clone());
                }
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

        public @Nullable Map<String, TargetV3> targets = new LinkedHashMap<>();

        public @Nullable String assetDir;

        /**
         * Processor-side cleaning policy, @Nullable per the @Nullable-exception rule of
         * the multi-versioning mechanic - no version bump. null means 'nothing to clean
         * because of this Function', the SourceCode's 'clean' option still applies.
         */
        public EnumsApi.@Nullable CleaningPolicy cleaningPolicy;

        public FunctionConfigYamlV3.@Nullable ApiV3 api = null;

        /**
         * Console-output rules for this Function, @Nullable per the @Nullable-exception rule of the
         * multi-versioning mechanic - no version bump.
         */
        public @Nullable List<AnalyzerV3> analyzers;
    }

    public FunctionConfigV3 function = new FunctionConfigV3();

    public @Nullable SystemV3 system = new SystemV3();

    /**
     * Payload storage of this Function, @Nullable per the @Nullable-exception rule of the
     * multi-versioning mechanic - no version bump. Defaults to null so that a function.yaml which
     * never declared it upgrades to a null dataStorage rather than to an empty one.
     */
    public @Nullable DataStorageV3 dataStorage = null;

}
