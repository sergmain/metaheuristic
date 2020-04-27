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

import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class ExperimentResultParamsYaml implements BaseParams {

    public final int version = 1;

    @Override
    public boolean checkIntegrity() {
        if (execContext ==null || experiment==null || taskIds==null) {
            throw new CheckIntegrityFailedException("(sourceCode==null || execContext==null || experiment==null || taskIds==null)");
        }
        return true;
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
    public static class ExecContextWithParams {
        public Long execContextId;
        public String execContextParams;
    }

    public long createdOn;
    public ExperimentWithParams experiment;
    public ExecContextWithParams execContext;
    public List<Long> taskIds = new ArrayList<>();
}
