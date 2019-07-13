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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class AtlasParamsYamlV1 {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class PlanWithParams {
        public Long planId;
        public String planParams;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class ExperimentWithParams {
        public Long experimentId;
        public String experimentParams;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class WorkbookWithParams {
        public Long workbookId;
        public String workbookParams;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class TaskWithParams {
        public Long taskId;
        public String taskParams;
    }

    public long createdOn;
    public final int version = 1;
    public PlanWithParams plan;
    public WorkbookWithParams workbook;
    public ExperimentWithParams experiment;
    public List<TaskWithParams> tasks = new ArrayList<>();
}
