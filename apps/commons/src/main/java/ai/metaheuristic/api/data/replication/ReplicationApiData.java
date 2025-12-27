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

package ai.metaheuristic.api.data.replication;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.sourcing.GitInfo;
import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 11/25/2020
 * Time: 5:15 PM
 */
public class ReplicationApiData {

    @Data
    public static class FunctionShortConfig {

        // code of function, i.e. simple-app:1.0
        public String code;
//        public String type;

        // Nullable for internal context, NonNull for external
        @Nullable
        public String file;

        //         * params for command line for invoking function
        //         * this isn't a holder for yaml-based config
//        public String params;
//        public String env;
        public EnumsApi.FunctionSourcing sourcing;
        @Nullable public Map<EnumsApi.HashAlgo, String> checksumMap;
//        @Nullable public GitInfo git;

        //         * this field tells Processor to don't add the absolute path to params.yaml file
        //         * as the last parameter in command line.
        //         * Useful for defining Function which is invoking curl as a command
//        public boolean skipParams = false;
        public final List<Map<String, String>> metas = new ArrayList<>();
    }

    @Data
    public static class FunctionConfigsReplication {
        public final List<FunctionShortConfig> configs = new ArrayList<>();
    }

}
