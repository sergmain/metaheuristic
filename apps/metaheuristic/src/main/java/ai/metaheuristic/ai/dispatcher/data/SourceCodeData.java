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

import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.dispatcher.SourceCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static ai.metaheuristic.api.EnumsApi.OperationStatus.OK;

/**
 * @author Serge
 * Date: 1/18/2020
 * Time: 4:41 PM
 */
@SuppressWarnings("unused")
public class SourceCodeData {

    public record OperationStatusWithSourceCodeId(OperationStatusRest status, @Nullable Long sourceCodeId) {
        public OperationStatusWithSourceCodeId(OperationStatusRest status, @Nullable Long sourceCodeId) {
            if (status.status==OK && sourceCodeId==null) {
                throw new IllegalStateException("(status.status==OK && sourceCodeId==null)");
            }
            this.status = status;
            this.sourceCodeId = null;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SourceCodeUid {
        public Long id;
        public String uid;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SourceCodeUidsForCompany extends BaseDataClass {
        public List<SourceCodeUid> items;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SourceCodesForCompany extends BaseDataClass {
        public List<SourceCode> items;
    }

    @Data
    public static class SourceCodeGraph {
        public boolean clean = false;
        public final List<ExecContextParamsYaml.Process> processes = new ArrayList<>();

        public final ExecContextParamsYaml.VariableDeclaration variables = new ExecContextParamsYaml.VariableDeclaration();
        public final DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph = new DirectedAcyclicGraph<>(DefaultEdge.class);
    }

    public static class SimpleProcess {
        public String code;
        public final List<String> preFunctions = new ArrayList<>();
        public String function;
        public final List<String> postFunctions = new ArrayList<>();
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class Development extends BaseDataClass {
        public String sourceCodeUid;
        public long sourceCodeId;
        public final List<SimpleProcess> processes = new ArrayList<>();

        public Development(List<String> errorMessage) {
            this.errorMessages = errorMessage;
        }

        public Development(String errorMessage) {
            this.errorMessages = Collections.singletonList(errorMessage);
        }
    }
}
