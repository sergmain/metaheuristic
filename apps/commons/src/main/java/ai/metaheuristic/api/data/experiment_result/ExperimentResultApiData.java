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

package ai.metaheuristic.api.data.experiment_result;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 4/26/2020
 * Time: 11:49 PM
 */
public class ExperimentResultApiData {

    @Data
    @NoArgsConstructor
    public static class ExperimentResultData {
        public Long id;
        public Integer version;
        public Long execContextId;
        public String code;
        public String name;
        public String description;
        public long createdOn;
        public int numberOfTask;
        public final List<ExperimentApiData.HyperParam> hyperParams = new ArrayList<>();

        public int state;
        public String getExecState() {
            return EnumsApi.ExecContextState.from(state);
        }
    }

}
