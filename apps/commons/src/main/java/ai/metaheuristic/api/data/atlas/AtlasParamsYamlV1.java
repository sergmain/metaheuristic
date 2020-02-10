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

package ai.metaheuristic.api.data.atlas;

import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class AtlasParamsYamlV1 implements BaseParams {

    @Override
    public boolean checkIntegrity() {
        if (plan==null || workbook==null || experiment==null || tasks==null) {
            throw new IllegalArgumentException("(sourceCode==null || workbook==null || experiment==null || tasks==null)");
        }
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class PlanWithParamsV1 {
        public Long planId;
        public String planParams;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class ExperimentWithParamsV1 {
        public Long experimentId;
        public String experimentParams;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class WorkbookWithParamsV1 {
        public Long workbookId;
        public String workbookParams;
        public int execState;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class TaskWithParamsV1 {
        public Long taskId;
        public String taskParams;
        public int execState;
        public String metrics;
        public String exec;

        public Long completedOn;
        public boolean completed;
        public Long assignedOn;
        public String typeAsString;
    }

    public long createdOn;
    public final int version = 1;
    public PlanWithParamsV1 plan;
    public WorkbookWithParamsV1 workbook;
    public ExperimentWithParamsV1 experiment;
    public List<TaskWithParamsV1> tasks = new ArrayList<>();
}
