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

package ai.metaheuristic.api.data.experiment_result;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Serge
 * Date: 8/3/2019
 * Time: 12:57 PM
 */

@Data
@NoArgsConstructor
public class ExperimentResultTaskParamsYamlV1 implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        if (taskId==null) {
            throw new IllegalArgumentException("(taskId==null)");
        }
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricsV1 {
        public EnumsApi.MetricsStatus status;
        public String error;
        public LinkedHashMap<String, BigDecimal> values = new LinkedHashMap<>();
    }

    @Data
    @NoArgsConstructor
    public static class TaskParamsV1 {
        public final Map<String, String> allInline = new HashMap<>();
        public final Map<String, String> inline = new HashMap<>();

        public TaskParamsV1(final Map<String, String> allInline, final Map<String, String> inline) {
            this.allInline.putAll(allInline);
            this.allInline.putAll(inline);
        }
    }

    public final MetricsV1 metrics = new MetricsV1();
    public EnumsApi.Fitting fitting;

    public Long taskId;
    public TaskParamsV1 taskParams;
    public int execState;

    public Long completedOn;
    public boolean completed;
    public Long assignedOn;
    public String typeAsString;

    public String functionExecResults;
}
