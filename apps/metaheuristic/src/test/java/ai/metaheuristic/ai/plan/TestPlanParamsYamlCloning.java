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

package ai.metaheuristic.ai.plan;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Serge
 * Date: 1/11/2020
 * Time: 10:38 PM
 */
public class TestPlanParamsYamlCloning {

    @Test
    public void cloneProcess() {
        PlanParamsYaml.Process p = new PlanParamsYaml.Process();
        p.name = "name";
        p.code = "code";
        p.type = EnumsApi.ProcessType.EXPERIMENT;
        p.collectResources = true;
        p.snippets = List.of(
                new PlanParamsYaml.SnippetDefForPlan("snippet-code", "snippet-params")
        );
        p.preSnippets = List.of(
                new PlanParamsYaml.SnippetDefForPlan("pre1-code", "pre1-params"),
                new PlanParamsYaml.SnippetDefForPlan("pre2-code", "pre2-params")
        );
        p.postSnippets = List.of(
                new PlanParamsYaml.SnippetDefForPlan("post1-code", "post1-params"),
                new PlanParamsYaml.SnippetDefForPlan("post2-code", "post2-params"),
                new PlanParamsYaml.SnippetDefForPlan("post3-code", "post3-params")
        ) ;
        p.parallelExec = true;

        p.timeoutBeforeTerminate = 120L;

        p.inputResourceCode = "input-code";
        p.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);;
        p.outputResourceCode = "output-code";
        p.metas.add(new Meta("key", "value", "ext"));
        p.order = 42;

        PlanParamsYaml.Process p1 = p.clone();

        assertEquals("name", p.name);
        assertEquals("code", p.code);
        assertEquals(EnumsApi.ProcessType.EXPERIMENT, p.type);
        assertTrue(p.collectResources);
        assertEquals(1, p.snippets.size());
        assertEquals("snippet-code", p.snippets.get(0).code);
        assertEquals("snippet-params", p.snippets.get(0).params);

        assertEquals(2, p.preSnippets.size());
        assertEquals("pre1-code", p.preSnippets.get(0).code);
        assertEquals("pre1-params", p.preSnippets.get(0).params);
        assertEquals("pre2-code", p.preSnippets.get(1).code);
        assertEquals("pre2-params", p.preSnippets.get(1).params);

        assertEquals(3, p.postSnippets.size());

        assertTrue(p.parallelExec);
        assertNotNull(p.timeoutBeforeTerminate);
        assertEquals(120L, (long)p.timeoutBeforeTerminate);

        assertEquals("input-code", p.inputResourceCode);
        assertNotNull(p.outputParams);
        assertEquals(EnumsApi.DataSourcing.launchpad, p.outputParams.sourcing);
        assertEquals("output-code", p.outputResourceCode);
        assertNotNull(p.metas);
        assertEquals(1, p.metas.size());
        assertEquals("key", p.metas.get(0).key);
        assertEquals("value", p.metas.get(0).value);
        assertEquals("ext", p.metas.get(0).ext);
        assertEquals(42, p.order);

    }
}
