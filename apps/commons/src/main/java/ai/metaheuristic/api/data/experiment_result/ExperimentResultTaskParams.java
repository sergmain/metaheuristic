/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
import javax.annotation.Nullable;

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
public class ExperimentResultTaskParams implements BaseParams {

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

    @Data
    @NoArgsConstructor
    public static class TaskParams {
        public final Map<String, String> allInline = new HashMap<>();
        public final Map<String, String> inline = new HashMap<>();

        public TaskParams(final Map<String, String> allInline, final Map<String, String> inline) {
            this.allInline.putAll(allInline);
            this.inline.putAll(inline);
        }
    }

    public final Metrics metrics = new Metrics();
    public EnumsApi.Fitting fitting;

    public Long taskId;
    public TaskParams taskParams;
    public int execState;

    @Nullable
    public Long completedOn;
    public boolean completed;
    @Nullable
    public Long assignedOn;
    @Nullable
    public String typeAsString;
    @Nullable
    public String functionExecResults;
}
