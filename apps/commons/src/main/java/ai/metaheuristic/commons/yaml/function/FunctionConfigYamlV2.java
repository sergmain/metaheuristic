/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
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
public class FunctionConfigYamlV2 implements BaseParams {

    public final int version=2;

    @Override
    public boolean checkIntegrity() {
        if (function.sourcing==null) {
            throw new CheckIntegrityFailedException("sourcing==null");
        }
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemV2 {
        public final Map<EnumsApi.HashAlgo, String> checksumMap = new HashMap<>();
        public String archive;
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(of = "code")
    public static class FunctionConfigV2 {

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
        public @Nullable List<Map<String, String>> metas = new ArrayList<>();

        public String src = CommonConsts.DEFAULT_FUNCTION_SRC_DIR;
    }


    public FunctionConfigV2 function = new FunctionConfigV2();

    public final SystemV2 system = new SystemV2();
}
