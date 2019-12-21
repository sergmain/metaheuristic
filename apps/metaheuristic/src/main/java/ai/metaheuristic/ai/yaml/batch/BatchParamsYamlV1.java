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

package ai.metaheuristic.ai.yaml.batch;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 8/29/2019
 * Time: 6:00 PM
 */
@Data
public class BatchParamsYamlV1 implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    public static class TaskStatusV1 {
        public long taskId;
        public long stationId;
        public String ip;
        public String host;
        public String execResults;
        public EnumsApi.TaskExecState state;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchStatusV1 {
        public final Map<String, String> renameTo = new HashMap<>();
        public String originArchiveName = Consts.RESULT_ZIP;
        // must be public for yaml's marshalling
        public boolean ok = false;
        // must be public for yaml's marshalling
        public String status = "";
    }

    public BatchStatusV1 batchStatus;
    public List<TaskStatusV1> taskStatuses;
    public boolean ok = false;
}
