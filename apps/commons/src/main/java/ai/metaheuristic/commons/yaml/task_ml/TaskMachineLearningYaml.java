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

package ai.metaheuristic.commons.yaml.task_ml;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Serge
 * Date: 10/14/2019
 * Time: 5:36 PM
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskMachineLearningYaml implements BaseParams {
    public final int version = 2;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metrics {
        public EnumsApi.MetricsStatus status;
        public String error;
        public String metrics;
    }

    public TaskMachineLearningYaml.Metrics metrics;
    public boolean overfitted;

    @Override
    public boolean checkIntegrity() {
        return true;
    }
}
