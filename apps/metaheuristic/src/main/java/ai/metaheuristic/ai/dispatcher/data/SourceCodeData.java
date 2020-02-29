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

import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 1/18/2020
 * Time: 4:41 PM
 */
public class SourceCodeData {

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class SourceCodesForCompany extends BaseDataClass {
        // it must be full name of class, i.e. with packages
        // 2020-02-24 why?
        public List<ai.metaheuristic.api.dispatcher.SourceCode> items;
    }

    @Data
    public static class SourceCodeGraph {
        public boolean clean = false;
        public final List<ExecContextParamsYaml.Process> processes = new ArrayList<>();

        public final ExecContextParamsYaml.VariableDeclaration variables = new ExecContextParamsYaml.VariableDeclaration();
        public final DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph = new DirectedAcyclicGraph<>(DefaultEdge.class);
    }
}
