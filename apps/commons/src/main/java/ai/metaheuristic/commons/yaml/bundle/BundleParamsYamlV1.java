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
package ai.metaheuristic.commons.yaml.bundle;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Slf4j
public class BundleParamsYamlV1 implements BaseParams {

    public final int version=3;

    @Override
    public boolean checkIntegrity() {
        if (functions.isEmpty()) {
            log.warn("list of functions is empty");
        }
        List<String> errors = new ArrayList<>();
        for (FunctionConfigV3 function : functions) {
            if (!S.b(function.content) && !S.b(function.file)) {
                errors.add(S.f("function %s has both - content and file", function.code));
            }
            if (!S.b(function.content) && function.git!=null) {
                errors.add(S.f("function %s has both - content and git definitions", function.code));
            }
            if (!S.b(function.content) && function.sourcing!= EnumsApi.FunctionSourcing.processor) {
                errors.add(S.f("function %s has content but sourcing is %s, must be FunctionSourcing.processor", function.code, function.sourcing));
            }
            if (function.sourcing==EnumsApi.FunctionSourcing.processor && S.b(function.content) && S.b(function.file) && S.b(function.env)) {
                errors.add(S.f("function %s has a sourcing as %s but content, file, and env are empty", function.code, function.sourcing));
            }
            if (function.sourcing==EnumsApi.FunctionSourcing.dispatcher && S.b(function.file)) {
                errors.add(S.f("function %s has a sourcing as %s but file are empty", function.code, function.sourcing));
            }
        }
        if (!errors.isEmpty()) {
            throw new CheckIntegrityFailedException(errors.toString());
        }
        return true;
    }

    /**
     * this class must be equal to ai.metaheuristic.commons.yaml.function.FunctionConfigYaml
     *
     * TODO 2020-09-27 add unit test to confirm equality
     */
    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(of = "code")
    public static class FunctionConfigV3 implements Cloneable {

        @SneakyThrows
        public FunctionConfigV3 clone() {
            final FunctionConfigV3 clone = (FunctionConfigV3) super.clone();
            if (this.checksumMap != null) {
                clone.checksumMap = new HashMap<>(this.checksumMap);
            }
            if (this.metas != null) {
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
        @Nullable
        public String env;
        public EnumsApi.FunctionSourcing sourcing;
        @Nullable
        public Map<EnumsApi.HashAlgo, String> checksumMap;
        @Nullable
        public GitInfo git;
        public boolean skipParams = false;
        public List<Map<String, String>> metas = new ArrayList<>();
        @Nullable
        public String content;
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceCodeConfigV3 implements Cloneable {

        public String file;

        @Setter
        private EnumsApi.SourceCodeLang lang = null;

        public EnumsApi.SourceCodeLang getLang() {
            return lang==null ? EnumsApi.SourceCodeLang.yaml : lang;
        }
    }

    public List<FunctionConfigV3> functions = new ArrayList<>();
    public List<SourceCodeConfigV3> sourceCodes = new ArrayList<>();


}
