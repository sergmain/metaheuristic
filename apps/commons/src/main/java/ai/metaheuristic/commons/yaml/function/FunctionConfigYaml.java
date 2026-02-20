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

    public final int version=2;

    @Override
    public boolean checkIntegrity() {
        if (function.sourcing==null) {
            throw new CheckIntegrityFailedException("sourcing==null");
        }
        List<String> errors = new ArrayList<>();
        if (function.sourcing==EnumsApi.FunctionSourcing.dispatcher && S.b(function.file)) {
            errors.add(S.f("function %s has a sourcing as %s but file are empty", function.code, function.sourcing));
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
            return clone;
        }

        /**
         * code of function, i.e. simple-app:1.0
         */
        public String code;
        public @Nullable String type;
        public @Nullable String file;
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

        public String src = CommonConsts.DEFAULT_FUNCTION_SRC_DIR;

        @Nullable
        public String assetDir;
    }

    public FunctionConfig function = new FunctionConfig();

    public @Nullable System system = new System();

}
