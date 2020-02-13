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

package ai.metaheuristic.api.data.exec_context;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ExecContextParamsYaml implements BaseParams {

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    public static class ExecContextYaml {
        public Map<String, List<String>> variables = new HashMap<>();

        public boolean preservePoolNames;
    }

    @Data
    @EqualsAndHashCode(of = "taskId")
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskVertex {
        public Long taskId;
        public EnumsApi.TaskExecState execState =  EnumsApi.TaskExecState.NONE;
    }

    public final int version = 1;
    public ExecContextYaml execContextYaml = new ExecContextYaml();
    public String graph;

    @JsonIgnore
    public List<String> getAllPoolCodes() {
        List<String> codes = new ArrayList<>();
        execContextYaml.variables.values().forEach(codes::addAll);
        return codes;
    }
}
