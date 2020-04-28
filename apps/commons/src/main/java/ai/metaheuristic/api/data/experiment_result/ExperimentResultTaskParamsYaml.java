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
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.LinkedHashMap;

/**
 * @author Serge
 * Date: 8/3/2019
 * Time: 12:57 PM
 */

@Data
@NoArgsConstructor
public class ExperimentResultTaskParamsYaml implements BaseParams {

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
    public static class Metrics {
        public EnumsApi.MetricsStatus status;
        public String error;
        public final LinkedHashMap<String, BigDecimal> values = new LinkedHashMap<>();
    }

    public final Metrics metrics = new Metrics();
    public EnumsApi.Fitting fitting;

    public Long taskId;
    public String taskParams;
    public int execState;

    public Long completedOn;
    public boolean completed;
    public Long assignedOn;
    public @Nullable String typeAsString;

    public String functionExecResults;
}
