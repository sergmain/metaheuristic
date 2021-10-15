/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.data;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 9/30/2020
 * Time: 10:33 PM
 */
public class VariableData {

    @Data
    public static class VariableDataSource {
        @Nullable
        public Permutation permutation = null;

        public List<BatchTopLevelService.FileWithMapping> files = List.of();

        @Nullable
        public String inputVariableContent = null;

        public List<Pair<String, Boolean>> booleanVariables = List.of();

        public VariableDataSource(Permutation permutation) {
            this.permutation = permutation;
        }

        public VariableDataSource(Permutation permutation, List<Pair<String, Boolean>> booleanVariables) {
            this.permutation = permutation;
            this.booleanVariables = booleanVariables;
        }

        public VariableDataSource(String inputVariableContent) {
            if (inputVariableContent.length()==0) {
                throw new IllegalStateException("content of variable can't be of zero length, use nullable instead");
            }
            this.inputVariableContent = inputVariableContent;
        }

        public VariableDataSource(List<BatchTopLevelService.FileWithMapping> files) {
            this.files = files;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Permutation {
        @JsonProperty("pvs")
        public List<VariableUtils.VariableHolder> permutedVariables;

        @JsonProperty("pvn")
        public String permutedVariableName;

        @JsonProperty("inls")
        public Map<String, Map<String, String>> inlines;

        @Nullable
        @JsonProperty("ivn")
        public String inlineVariableName;

        @Nullable
        @JsonProperty("ip")
        public Map<String, String> inlinePermuted;

        @JsonProperty("pi")
        public boolean permuteInlines;
    }

    @Data
    @AllArgsConstructor
    public static class UploadVariableStatusResult {
        @Nullable
        public TaskImpl task;
        public Enums.UploadVariableStatus status;

        public UploadVariableStatusResult(Enums.UploadVariableStatus status) {
            this.status = status;
        }
    }
}
