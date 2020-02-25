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
    public static class Resource {
        public String id;
        public EnumsApi.VariableContext context;
        public String realName;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Variable {
        public String name;
        public EnumsApi.DataSourcing sourcing = EnumsApi.DataSourcing.dispatcher;
        public GitInfo git;
        public DiskInfo disk;
        public List<Resource> resources;
    }

    @Data
    public static class Task {
        public Long execContextId;
        public boolean clean = false;
        /**
         * Timeout before terminate a process with function
         * value in seconds
         * null or 0 mean the infinite execution
         */
        public Long timeoutBeforeTerminate;


        public Map<String, Map<String, String>> inline;

        public List<Variable> variables;

        // fields which are initialized at processor
        public String workingPath;
    }

    public Task task = new Task();

}
