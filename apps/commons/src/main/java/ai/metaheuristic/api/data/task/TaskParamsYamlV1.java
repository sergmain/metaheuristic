/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.api.data.task;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.api.sourcing.GitInfo;
import lombok.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 9:10 PM
 */
@Data
public class TaskParamsYamlV1 implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FunctionInfoV1 {
        public boolean signed;
        /**
         * function's binary length
         */
        public long length;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MachineLearningV1 {
        // does this function support metrics
        public boolean metrics = false;
        // does this function support fitting detection
        public boolean fitting = false;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TaskMachineLearningV1 {
        public Map<String, String> hyperParams;
    }

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(of = "code")
    public static class FunctionConfigV1 implements Cloneable {

        @SneakyThrows
        public FunctionConfigV1 clone() {
            final FunctionConfigV1 clone = (FunctionConfigV1) super.clone();
            if (this.checksumMap != null) {
                clone.checksumMap = new HashMap<>(this.checksumMap);
            }
            if (this.metas != null) {
                clone.metas = new ArrayList<>();
                for (Meta meta : this.metas) {
                    clone.metas.add(new Meta(meta.key, meta.value, meta.ext));
                }
            }
            return clone;
        }

        /**
         * code of function, i.e. simple-app:1.0
         */
        public String code;
        public String type;
        public String file;
        /**
         * params for command line for invoking function
         * <p>
         * this isn't a holder for yaml-based config
         */
        public String params;
        public String env;
        public EnumsApi.FunctionSourcing sourcing;
        public Map<EnumsApi.Type, String> checksumMap;
        public FunctionInfoV1 info = new FunctionInfoV1();
        public String checksum;
        public GitInfo git;
        public boolean skipParams = false;
        public List<Meta> metas = new ArrayList<>();
        public MachineLearningV1 ml;
    }

    @Data
    public static class TaskYamlV1 {
        public Long execContextId;
        public FunctionConfigV1 function;
        public List<FunctionConfigV1> preFunctions;
        public List<FunctionConfigV1> postFunctions;

        // key is ???, value is ???
        public Map<String, List<String>> inputResourceIds = new HashMap<>();

        // key is ???, value is ???
        public Map<String, String> outputResourceIds = new HashMap<>();
        public Map<String, DataStorageParams> resourceStorageUrls = new HashMap<>();
        public TaskMachineLearningV1 taskMl;
        public boolean clean = false;
        public EnumsApi.FunctionExecContext context;

        /**
         * Timeout before terminate a process with function
         * value in seconds
         * null or 0 mean the infinite execution
         */
        public Long timeoutBeforeTerminate;

        // fields which are initialized at station
        public String workingPath;

        // key - resource code, value - real file name of resource
        public Map<String, String> realNames;
    }

    public TaskYamlV1 taskYaml = new TaskYamlV1();

}
