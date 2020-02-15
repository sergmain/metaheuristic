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
        if (sourceCode ==null || execContext ==null || experiment==null || taskIds==null) {
            throw new IllegalArgumentException("(sourceCode==null || execContext==null || experiment==null || taskIds==null)");
        }
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class SourceCodeWithParamsV1 {
        public Long sourceCodeId;
        public String sourceCodeParams;
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
    public static class ExecContextWithParamsV1 {
        public Long execContextId;
        public String execContextParams;
        public int execState;
    }

    public long createdOn;
    public final int version = 1;
    public SourceCodeWithParamsV1 sourceCode;
    public ExecContextWithParamsV1 execContext;
    public ExperimentWithParamsV1 experiment;
    public List<Long> taskIds = new ArrayList<>();
}
