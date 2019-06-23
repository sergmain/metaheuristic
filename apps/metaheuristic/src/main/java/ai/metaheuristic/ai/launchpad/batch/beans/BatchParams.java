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

package ai.metaheuristic.ai.launchpad.batch.beans;

import ai.metaheuristic.api.EnumsApi;
import lombok.Data;

import java.util.List;

/**
 * @author Serge
 * Date: 5/29/2019
 * Time: 11:38 PM
 */
@Data
public class BatchParams {

    @Data
    public static class TaskStatus {
        public long taskId;
        public long stationId;
        public String ip;
        public String host;
        public String execResults;
        public EnumsApi.TaskExecState state;
    }

    public BatchStatus batchStatus;
    public List<TaskStatus> taskStatuses;
    public boolean ok = false;

}
