/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.ai.graph;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextProcessGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskProducingService;
import ai.metaheuristic.ai.dispatcher.source_code.graph.SourceCodeGraphLanguageYaml;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static ai.metaheuristic.api.EnumsApi.FunctionExecContext.*;
import static ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml.FunctionDefinition;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 4/10/2020
 * Time: 8:32 PM
 */
public class TestProcessGraph {

    @Test
    public void test() {

        ExecContextParamsYaml ecpy = new ExecContextParamsYaml();
        ecpy.processesGraph = ConstsApi.EMPTY_GRAPH;

        DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph = ExecContextProcessGraphService.importProcessGraph(ecpy);

        Map<String, Long> ids = new HashMap<>();
        AtomicLong currId = new AtomicLong();

        ExecContextData.ProcessVertex v1 = SourceCodeGraphLanguageYaml.createProcessVertex(ids, currId, "code-1", Consts.TOP_LEVEL_CONTEXT_ID);
        ExecContextProcessGraphService.addProcessVertexToGraph(processGraph, v1, List.of());

        ExecContextData.ProcessVertex v2 = SourceCodeGraphLanguageYaml.createProcessVertex(ids, currId, "code-2", Consts.TOP_LEVEL_CONTEXT_ID);
        ExecContextProcessGraphService.addProcessVertexToGraph(processGraph, v2, List.of(v1));

        System.out.println(ExecContextProcessGraphService.asString(processGraph));

        List<ExecContextData.ProcessVertex> list = ExecContextProcessGraphService.findTargets(processGraph, "code-1");
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("code-2", list.get(0).process);
    }

    public static final String processGraphAsStr =
            """
            strict digraph G {
              1 [ process_context_id="1" process="mh.inline-as-variable" ];
              2 [ process_context_id="1" process="assembly-raw-file" ];
              3 [ process_context_id="1" process="dataset-processing" ];
              4 [ process_context_id="1,2" process="feature-processing-1" ];
              5 [ process_context_id="1,3" process="feature-processing-2" ];
              6 [ process_context_id="1" process="permute-values-of-variables" ];
              7 [ process_context_id="1,4" process="mh.permute-variables" ];
              8 [ process_context_id="1,4,5" process="fit-dataset" ];
              9 [ process_context_id="1,4,5" process="predict-dataset" ];
              10 [ process_context_id="1" process="mh.aggregate" ];
              11 [ process_context_id="1" process="mh.finish" ];
              1 -> 2;
              2 -> 3;
              3 -> 4;
              3 -> 5;
              3 -> 6;
              4 -> 6;
              5 -> 6;
              6 -> 7;
              7 -> 8;
              8 -> 9;
              6 -> 10;
              7 -> 10;
              9 -> 10;
              10 -> 11;
            }
            """;
    private static ExecContextParamsYaml initExecContextParamYaml() {
        ExecContextParamsYaml ecpy = new ExecContextParamsYaml();
        init(Consts.MH_INLINE_AS_VARIABLE_FUNCTION, internal, Consts.TOP_LEVEL_CONTEXT_ID, ecpy);
        init("assembly-raw-file", external, Consts.TOP_LEVEL_CONTEXT_ID, ecpy);
        init("dataset-processing", external, Consts.TOP_LEVEL_CONTEXT_ID, ecpy);
        init("feature-processing-1", external, "1,2", ecpy);
        init("permute-values-of-variables", external, "1", ecpy);
        init("mh.permute-variables", internal, "1,4", ecpy);
        init("fit-dataset", external, "1,4,5", ecpy);
        init("predict-dataset", external, "1,4,5", ecpy);


        return ecpy;
    }


    @Test
    public void test_findAncestors() {
        ExecContextParamsYaml ecpy = initExecContextParamYaml();

        DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph = ExecContextProcessGraphService.importProcessGraph(processGraphAsStr);
        ExecContextData.ProcessVertex v = ExecContextProcessGraphService.findVertex(processGraph, "feature-processing-1");
        assertNotNull(v);
        assertEquals(4, v.id);
        assertEquals("1,2", v.processContextId);
        assertNull(ExecContextTaskProducingService.checkForInternalFunctionAsParent(ecpy, processGraph, v));

        ExecContextData.ProcessVertex v1 = ExecContextProcessGraphService.findVertex(processGraph, "dataset-processing");
        assertNotNull(v1);
        assertNull(ExecContextTaskProducingService.checkForInternalFunctionAsParent(ecpy, processGraph, v1));

        ExecContextData.ProcessVertex v2 = ExecContextProcessGraphService.findVertex(processGraph, "permute-values-of-variables");
        assertNotNull(v2);
        assertNull(ExecContextTaskProducingService.checkForInternalFunctionAsParent(ecpy, processGraph, v2));

        ExecContextData.ProcessVertex v3 = ExecContextProcessGraphService.findVertex(processGraph, "mh.permute-variables");
        assertNotNull(v3);
        assertNull(ExecContextTaskProducingService.checkForInternalFunctionAsParent(ecpy, processGraph, v3));

        ExecContextData.ProcessVertex v4 = ExecContextProcessGraphService.findVertex(processGraph, "fit-dataset");
        assertNotNull(v4);
        assertNotNull(ExecContextTaskProducingService.checkForInternalFunctionAsParent(ecpy, processGraph, v4));

        ExecContextData.ProcessVertex v5 = ExecContextProcessGraphService.findVertex(processGraph, "predict-dataset");
        assertNotNull(v5);
        assertNotNull(ExecContextTaskProducingService.checkForInternalFunctionAsParent(ecpy, processGraph, v5));

    }

    private static void init(String code, EnumsApi.FunctionExecContext context, String internalContextId, ExecContextParamsYaml ecpy) {
        FunctionDefinition f4 = new FunctionDefinition(code, "", context, EnumsApi.FunctionRefType.code);
        ExecContextParamsYaml.Process p4 = new ExecContextParamsYaml.Process(code, code, internalContextId, f4);
        ecpy.processes.add(p4);
    }
}
