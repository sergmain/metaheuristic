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

package ai.metaheuristic.ai.launchpad.data;

import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
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
        public List<ai.metaheuristic.api.launchpad.SourceCode> items;
    }

    @Data
    @EqualsAndHashCode(of = "taskId")
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleTaskVertex {
        public Long taskId;
        public String execContextId;
        public String internalContextId;

        public String processName;
        public String processCode;
        public SourceCodeParamsYaml.SnippetDefForSourceCode snippet;
        public List<SourceCodeParamsYaml.SnippetDefForSourceCode> preSnippets;
        public List<SourceCodeParamsYaml.SnippetDefForSourceCode> postSnippets;

        /**
         * Timeout before terminating a process with snippet
         * value in seconds
         * null or 0 mean the infinite execution
         */
        public Long timeoutBeforeTerminate;
        public final List<String> input = new ArrayList<>();
        public final List<String> output = new ArrayList<>();
        public List<Meta> metas = new ArrayList<>();
    }

    @Data
    public static class SourceCodeGraph {
        public boolean clean;
        public final DirectedAcyclicGraph<SimpleTaskVertex, DefaultEdge> graph = new DirectedAcyclicGraph<>(DefaultEdge.class);
    }
}
