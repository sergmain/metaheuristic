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
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 9:10 PM
 */
@Data
public class TaskFileParamsYamlV1 implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ResourceV1 {
        public String id;
        // real file name of resource, is present
        public String realName;

        public ResourceV1(String id) {
            this.id = id;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InputVariableV1 {
        public String name;
        public EnumsApi.DataSourcing sourcing = EnumsApi.DataSourcing.dispatcher;
        public GitInfo git;
        public DiskInfo disk;
        public final List<ResourceV1> resources = new ArrayList<>();

        public InputVariableV1(String name, EnumsApi.DataSourcing sourcing) {
            this.name = name;
            this.sourcing = sourcing;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OutputVariableV1 {
        public String name;
        public EnumsApi.DataSourcing sourcing = EnumsApi.DataSourcing.dispatcher;
        public GitInfo git;
        public DiskInfo disk;
        public ResourceV1 resources;
    }

    @Data
    public static class TaskYamlV1 {
        public Long execContextId;
        public boolean clean = false;
        public Map<String, Map<String, String>> inline;

        public final List<InputVariableV1> inputs = new ArrayList<>();
        public final List<OutputVariableV1> outputs = new ArrayList<>();

        // fields which are initialized at processor
        public String workingPath;
    }

    public TaskYamlV1 task = new TaskYamlV1();

}
