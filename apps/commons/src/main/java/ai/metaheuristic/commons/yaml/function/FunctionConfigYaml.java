/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.*;
import org.springframework.lang.Nullable;

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
        if (function.sourcing==EnumsApi.FunctionSourcing.processor && S.b(function.file) && S.b(function.env)) {
            errors.add(S.f("function %s has a sourcing as %s but content, file, and env are empty", function.code, function.sourcing));
        }
        if (function.sourcing==EnumsApi.FunctionSourcing.dispatcher && S.b(function.file)) {
            errors.add(S.f("function %s has a sourcing as %s but file are empty", function.code, function.sourcing));
        }
        if (MetaUtils.getValue(function.metas, ConstsApi.META_MH_TASK_PARAMS_VERSION)==null) {
            errors.add(S.f("function %s must have a meta 'mh.task-params-version' with effective version of TaskParams", function.code));
        }
        if (function.metas!=null) {
            for (Map<String, String> meta : function.metas) {
                if (meta.size()!=1) {
                    errors.add(S.f("function %s has an incorrectly defined meta, mest be one meta per yaml element, %s", function.code, meta));
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
        return clone;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class System implements Cloneable {
        public final Map<EnumsApi.HashAlgo, String> checksumMap = new HashMap<>();
        public String archive;

        @SneakyThrows
        public System clone() {
            final System clone = (System) super.clone();
            clone.checksumMap.putAll(this.checksumMap);
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
        @Nullable
        public String type;

        @Nullable
        public String file;
        /**
         * params for command line for invoking function
         * <p>
         * this isn't a holder for yaml-based config
         */
        @Nullable
        public String params;

        public String env;
        public EnumsApi.FunctionSourcing sourcing;
        @Nullable
        public GitInfo git;
        @Nullable
        public List<Map<String, String>> metas = new ArrayList<>();
    }

    public FunctionConfig function = new FunctionConfig();

    @Nullable
    public System system;

}
