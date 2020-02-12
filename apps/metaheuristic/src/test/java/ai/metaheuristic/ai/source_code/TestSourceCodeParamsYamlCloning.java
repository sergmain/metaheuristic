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

package ai.metaheuristic.ai.source_code;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Serge
 * Date: 1/11/2020
 * Time: 10:38 PM
 */
public class TestSourceCodeParamsYamlCloning {

    @Test
    public void cloneProcess() {
        SourceCodeParamsYaml.Process p = new SourceCodeParamsYaml.Process();
        p.name = "name";
        p.code = "code";
        p.snippet = new SourceCodeParamsYaml.SnippetDefForSourceCode("snippet-code", "snippet-params", EnumsApi.SnippetExecContext.external);
        p.preSnippets = List.of(
                new SourceCodeParamsYaml.SnippetDefForSourceCode("pre1-code", "pre1-params", EnumsApi.SnippetExecContext.external),
                new SourceCodeParamsYaml.SnippetDefForSourceCode("pre2-code", "pre2-params", EnumsApi.SnippetExecContext.external)
        );
        p.postSnippets = List.of(
                new SourceCodeParamsYaml.SnippetDefForSourceCode("post1-code", "post1-params", EnumsApi.SnippetExecContext.external),
                new SourceCodeParamsYaml.SnippetDefForSourceCode("post2-code", "post2-params", EnumsApi.SnippetExecContext.external),
                new SourceCodeParamsYaml.SnippetDefForSourceCode("post3-code", "post3-params", EnumsApi.SnippetExecContext.external)
        ) ;

        p.timeoutBeforeTerminate = 120L;

        p.output.add( new SourceCodeParamsYaml.Variable("output-code"));
        p.metas.add(new Meta("key", "value", "ext"));

        SourceCodeParamsYaml.Process p1 = p.clone();

        assertEquals("name", p1.name);
        assertEquals("code", p1.code);
        assertNotNull(p1.snippet);
        assertEquals("snippet-code", p1.snippet.code);
        assertEquals("snippet-params", p1.snippet.params);

        assertEquals(2, p1.preSnippets.size());
        assertEquals("pre1-code", p1.preSnippets.get(0).code);
        assertEquals("pre1-params", p1.preSnippets.get(0).params);
        assertEquals("pre2-code", p1.preSnippets.get(1).code);
        assertEquals("pre2-params", p1.preSnippets.get(1).params);

        assertEquals(3, p1.postSnippets.size());

        assertNotNull(p1.timeoutBeforeTerminate);
        assertEquals(120L, (long)p1.timeoutBeforeTerminate);

        assertNotNull(p1.output);
        assertEquals(1, p1.output.size());
        SourceCodeParamsYaml.Variable params = p1.output.get(0);

        assertEquals(EnumsApi.DataSourcing.launchpad, params.sourcing);
        assertEquals("output-code", params.name);

        assertNotNull(p.metas);
        assertEquals(1, p1.metas.size());
        assertEquals("key", p1.metas.get(0).key);
        assertEquals("value", p1.metas.get(0).value);
        assertEquals("ext", p1.metas.get(0).ext);

    }
}
