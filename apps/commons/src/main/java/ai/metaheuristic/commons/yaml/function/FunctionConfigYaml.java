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
import ai.metaheuristic.api.sourcing.DiskInfo;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.FunctionAnalyzerUtils;
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
        // ❗ Analyzer rules are checked HERE, at parse, rather than at any one upload path. A rule that
        // names a scope only the dispatcher may set, or a timeout nobody can read, would otherwise load
        // cleanly and then silently never block anything - and the author would believe it was in force.
        // Failing at parse is the only point that covers every way a descriptor enters the system.
        if (function.analyzers!=null) {
            for (Analyzer analyzer : function.analyzers) {
                if (S.b(analyzer.name)) {
                    errors.add(S.f("function %s declares an analyzer with no name", function.code));
                    continue;
                }
                try {
                    FunctionAnalyzerUtils.checkScopeAllowedInDescriptor(analyzer);
                    FunctionAnalyzerUtils.parseTimeout(analyzer.timeout);
                }
                catch (IllegalStateException e) {
                    errors.add(S.f("function %s, analyzer '%s': %s", function.code, analyzer.name, e.getMessage()));
                }
                if (analyzer.regex==null || analyzer.regex.isEmpty()) {
                    errors.add(S.f("function %s, analyzer '%s' declares no regex, so it can never match",
                            function.code, analyzer.name));
                    continue;
                }
                for (String regex : analyzer.regex) {
                    try {
                        java.util.regex.Pattern.compile(regex);
                    }
                    catch (java.util.regex.PatternSyntaxException e) {
                        errors.add(S.f("function %s, analyzer '%s' declares a regex which doesn't compile: %s",
                                function.code, analyzer.name, regex));
                    }
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

    /**
     * Where this Function's payload lives and what is known about it.
     *
     * <p>Moved here from {@code FunctionData.params}, which used to hold a whole DataStorageParams
     * document next to the bytes. Keeping it there meant the row that stores the payload also decided
     * whether the payload was allowed to be stored - a rule about the Function expressed by its blob.
     * It belongs to the Function's own descriptor, so the blob row now carries bytes only.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataStorage implements Cloneable {

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

        public DataStorage(EnumsApi.DataSourcing sourcing, String name) {
            this.sourcing = sourcing;
            this.name = name;
        }

        @SneakyThrows
        public DataStorage clone() {
            final DataStorage clone = (DataStorage) super.clone();
            if (this.checksumMap!=null) {
                clone.checksumMap = new HashMap<>(this.checksumMap);
            }
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

    /**
     * A rule that says what a Function's own console output means when the Function fails.
     *
     * <p>Declared by the Function author because they are the ones who know what their tool prints
     * when its API key is exhausted, when the host it runs on is broken, or when the failure is
     * simply not worth retrying. The dispatcher supplies the identity — which key, which Function,
     * which Processor — and this only supplies the semantics.
     *
     * <p>Named {@code analyzers} rather than after the component that enforces it: at load time
     * nothing is blocked, these are rules that MIGHT open a block later. ❗ It also ships inside every
     * signed Function bundle, so renaming it later means re-signing and redeploying the fleet — it must
     * not be coupled to a dispatcher-internal class name.
     */
    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Analyzer implements Cloneable {
        /** Free text. Becomes the recorded reason when this rule matches. */
        public String name;

        /** Any one hit is enough. No implicit flags — an author wanting case-insensitivity writes {@code (?i)}. */
        public @Nullable List<String> regex = new ArrayList<>();

        /** How long to withhold work for. {@code ms | s | min | h | d}, e.g. {@code 20min}. */
        public String timeout;

        /**
         * Whether the failing Task's retry counter advances. {@code false} gives a free retry, which is
         * right when the failure was never the Task's fault.
         */
        public boolean incrementTries;

        /**
         * What the block covers. Only {@code api}, {@code function} and {@code processor} may be
         * declared here — {@code global} and {@code company} are dispatcher-only and are rejected.
         */
        public EnumsApi.GateScope scope;

        @SneakyThrows
        public Analyzer clone() {
            final Analyzer clone = (Analyzer) super.clone();
            if (this.regex!=null) {
                clone.regex = new ArrayList<>(this.regex);
            }
            return clone;
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
            if (this.targets!=null) {
                clone.targets = new LinkedHashMap<>();
                for (Map.Entry<String, Target> e : this.targets.entrySet()) {
                    clone.targets.put(e.getKey(), e.getValue().clone());
                }
            }
            if (this.analyzers!=null) {
                clone.analyzers = new ArrayList<>();
                for (Analyzer a : this.analyzers) {
                    clone.analyzers.add(a.clone());
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

        /**
         * Console-output rules for this Function, @Nullable per the @Nullable-exception rule of the
         * multi-versioning mechanic - no version bump. null means the Function declares none, which is
         * the normal case.
         */
        public @Nullable List<Analyzer> analyzers;
    }

    public FunctionConfig function = new FunctionConfig();

    public @Nullable System system = new System();

    /**
     * Payload storage of this Function. @Nullable per the @Nullable-exception rule of the
     * multi-versioning mechanic - no version bump.
     *
     * <p>❗ Defaults to null, not to an empty DataStorage, and the default is load-bearing. This is
     * dispatcher-side state - it is assigned when the dispatcher takes the payload in
     * (FunctionTxService.persistFunction), and a Function author never writes it in function.yaml.
     * An empty default would be dumped as {@code dataStorage: {}} by PackageBundle, which round-trips
     * every bundled function.yaml through this class, putting a dispatcher concept into a distributed
     * artifact that has no dispatcher. null is skipped by the representer, so the descriptor an author
     * wrote comes out of the packager unchanged.
     */
    public @Nullable DataStorage dataStorage = null;

}
