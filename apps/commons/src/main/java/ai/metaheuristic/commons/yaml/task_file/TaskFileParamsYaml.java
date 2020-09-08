/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.commons.yaml.task_file;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.sourcing.DiskInfo;
import ai.metaheuristic.api.sourcing.GitInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class is being used for storing a parameters of task for function in a file, ie params-v1.yaml
 *
 * Class TaskParamsYaml is for storing parameters of task internally at Processor side
 *
 * @author Serge
 * Date: 6/17/2019
 * Time: 9:10 PM
 */
@Data
@EqualsAndHashCode
public class TaskFileParamsYaml implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InputVariable {
        public String id;
        public String name;
        public EnumsApi.DataSourcing sourcing = EnumsApi.DataSourcing.dispatcher;
        public @Nullable GitInfo git;
        public @Nullable DiskInfo disk;
        public EnumsApi.DataType dataType;

        public @Nullable String filename;
        public @Nullable String type;

        public boolean empty = false;
        private Boolean nullable;

        public Boolean getNullable() {
            return nullable==null ? false : nullable;
        }

        public void setNullable(Boolean nullable) {
            this.nullable = nullable;
        }

        public InputVariable(Long id, String name, EnumsApi.DataSourcing sourcing) {
            this(id.toString(), name, sourcing);
        }

        public InputVariable(String id, String name, EnumsApi.DataSourcing sourcing) {
            this.id = id;
            this.name = name;
            this.sourcing = sourcing;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OutputVariable {
        public String id;
        public String name;
        public EnumsApi.DataSourcing sourcing = EnumsApi.DataSourcing.dispatcher;
        public @Nullable GitInfo git;
        public @Nullable DiskInfo disk;
        public @Nullable String filename;
        public EnumsApi.DataType dataType;
        public @Nullable String type;
        public boolean empty = false;
        private Boolean nullable;

        public Boolean getNullable() {
            return nullable==null ? false : nullable;
        }

        public void setNullable(Boolean nullable) {
            this.nullable = nullable;
        }

        public OutputVariable(Long id, String name, EnumsApi.DataSourcing sourcing) {
            this(id.toString(), name, sourcing);
        }

        public OutputVariable(String id, String name, EnumsApi.DataSourcing sourcing) {
            this.id = id;
            this.name = name;
            this.sourcing = sourcing;
        }
    }

    @Data
    public static class Task {
        public Long execContextId;
        public boolean clean = false;

        public @Nullable Map<String, Map<String, String>> inline;

        public final List<InputVariable> inputs = new ArrayList<>();
        public final List<OutputVariable> outputs = new ArrayList<>();

        // fields which are initialized at processor
        public String workingPath;
    }

    public Task task = new Task();

}
