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

package ai.metaheuristic.ai.graph;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextProcessGraphService;
import ai.metaheuristic.ai.dispatcher.source_code.graph.SourceCodeGraphLanguageYaml;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 4/10/2020
 * Time: 8:32 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@Slf4j
public class TestProcessGraph {

    @Test
    public void test() {

        ExecContextParamsYaml ecpy = new ExecContextParamsYaml();
        ecpy.processesGraph = ConstsApi.EMPTY_GRAPH;

        DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph = ExecContextProcessGraphService.importProcessGraph(ecpy);

        Map<String, Long> ids = new HashMap<>();
        AtomicLong currId = new AtomicLong();

        ExecContextData.ProcessVertex v1 = SourceCodeGraphLanguageYaml.getVertex(ids, currId, "code-1", Consts.TOP_LEVEL_CONTEXT_ID);
        ExecContextProcessGraphService.addNewTasksToGraph(processGraph, v1, List.of());

        ExecContextData.ProcessVertex v2 = SourceCodeGraphLanguageYaml.getVertex(ids, currId, "code-2", Consts.TOP_LEVEL_CONTEXT_ID);
        ExecContextProcessGraphService.addNewTasksToGraph(processGraph, v2, List.of(v1));

        System.out.println(ExecContextProcessGraphService.asString(processGraph));

        List<ExecContextData.ProcessVertex> list = ExecContextProcessGraphService.findTargets(processGraph, "code-1");
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("code-2", list.get(0).process);
    }

}
