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

package ai.metaheuristic.ai.dispatcher.data;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.lang.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
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

        public VariableDataSource(Permutation permutation) {
            this.permutation = permutation;
        }

        public VariableDataSource(String inputVariableContent) {
            this.inputVariableContent = inputVariableContent;
        }

        public VariableDataSource(List<BatchTopLevelService.FileWithMapping> files) {
            this.files = files;
        }
    }

    @Data
    @AllArgsConstructor
    public static class Permutation {
        public List<VariableUtils.VariableHolder> permutedVariables;
        public String permutedVariableName;
        public Map<String, Map<String, String>> inlines;
        public String inlineVariableName;
        public Map<String, String> inlinePermuted;
   }

    @Data
    public static class DataStreamHolder {

        public DataStreamHolder() {
            TxUtils.checkTxNotExists();
        }

        public List<InputStream> inputStreams = new ArrayList<>();
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
