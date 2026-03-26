/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.api.data;

import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Sergio Lissner
 * Date: 3/11/2026
 * Time: 11:26 AM
 */
@Data
public class SourceCodeGraph {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessControl {
        public String groups;
    }


    public String uid;
    public boolean clean = false;
    public int instances;
    public @Nullable AccessControl ac = null;
    public @Nullable String type = null;
    public List<Map<String, String>> metas = new ArrayList<>();

    public final List<ExecContextParamsYaml.Process> processes = new ArrayList<>();

    public final ExecContextParamsYaml.VariableDeclaration variables = new ExecContextParamsYaml.VariableDeclaration();
    public final DirectedAcyclicGraph<ExecContextApiData.ProcessVertex, DefaultEdge> processGraph = new DirectedAcyclicGraph<>(DefaultEdge.class);

}
