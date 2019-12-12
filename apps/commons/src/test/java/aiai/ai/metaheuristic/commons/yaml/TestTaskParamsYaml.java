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

package aiai.ai.metaheuristic.commons.yaml;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYamlV2;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Serge
 * Date: 8/8/2019
 * Time: 6:35 PM
 */
public class TestTaskParamsYaml {

    @Test
    public void test() {
        TaskParamsYamlV2 v2 = new TaskParamsYamlV2();
        final TaskParamsYamlV2.TaskYamlV2 ty = new TaskParamsYamlV2.TaskYamlV2();
        v2.taskYaml = ty;

        final TaskParamsYamlV2.SnippetConfigV2 preSnippet = new TaskParamsYamlV2.SnippetConfigV2();
        preSnippet.code = "pre-snippet-code";
        preSnippet.sourcing = EnumsApi.SnippetSourcing.station;
        ty.preSnippets = List.of(preSnippet);

        final TaskParamsYamlV2.SnippetConfigV2 snippet = new TaskParamsYamlV2.SnippetConfigV2();
        snippet.code = "snippet-code";
        snippet.sourcing = EnumsApi.SnippetSourcing.git;
        ty.snippet = snippet;

        final TaskParamsYamlV2.SnippetConfigV2 postSnippet = new TaskParamsYamlV2.SnippetConfigV2();
        postSnippet.code = "post-snippet-code";
        postSnippet.sourcing = EnumsApi.SnippetSourcing.launchpad;
        ty.postSnippets = List.of(postSnippet);

        //Map<String, String> realNames = Map.of("file-code-01", "real-name-01","file-code-02", "real-name-02");
        ty.clean = true;
        ty.hyperParams = Map.of("hyper-param-key-01", "hyper-param-value-01");
        ty.workingPath = "working-path";

        String s = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(v2);
        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(s);

        assertNotNull(tpy);
        assertEquals(3, tpy.version);
        assertNotNull(tpy.taskYaml);
        assertNotNull(tpy.taskYaml.preSnippets);
        assertNotNull(tpy.taskYaml.snippet);
        assertNotNull(tpy.taskYaml.postSnippets);
        assertNotNull(tpy.taskYaml.hyperParams);
        assertNotNull(tpy.taskYaml.workingPath);

        assertTrue(tpy.taskYaml.clean);
        assertEquals("working-path", tpy.taskYaml.workingPath);

        assertEquals(1, tpy.taskYaml.preSnippets.size());
        assertEquals("pre-snippet-code", tpy.taskYaml.preSnippets.get(0).code);
        assertEquals(EnumsApi.SnippetSourcing.station, tpy.taskYaml.preSnippets.get(0).sourcing);

        assertEquals("snippet-code", tpy.taskYaml.snippet.code);
        assertEquals(EnumsApi.SnippetSourcing.git, tpy.taskYaml.snippet.sourcing);

        assertEquals(1, tpy.taskYaml.postSnippets.size());
        assertEquals("post-snippet-code", tpy.taskYaml.postSnippets.get(0).code);
        assertEquals(EnumsApi.SnippetSourcing.launchpad, tpy.taskYaml.postSnippets.get(0).sourcing);

        assertEquals(1, tpy.taskYaml.hyperParams.size());
        assertTrue(tpy.taskYaml.hyperParams.containsKey("hyper-param-key-01"));
        assertEquals("hyper-param-value-01", tpy.taskYaml.hyperParams.get("hyper-param-key-01"));

    }
}
