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

package ai.metaheuristic.commons.graph.source_code_graph;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.SourceCodeGraph;
import ai.metaheuristic.api.data.exec_context.ExecContextParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A yaml SourceCode is parsed by SnakeYAML, which keeps each scalar's yaml type - an unquoted {@code true}
 * is a Boolean, an unquoted {@code 7} an Integer - even inside a {@code Map<String, String>}. Process metas
 * and inline variables are copied from that model into {@link ExecContextParams}, whose declared value type
 * is String and which is stored as JSON: a typed mapper writes a Map&lt;String, String&gt; value with the
 * String serializer and fails on anything else.
 *
 * <p>The values are read through a raw {@code Map<?, ?>} on purpose: the declared String type is exactly
 * what is under test, so the read must not go through it.
 *
 * @author Sergio Lissner
 * Date: 9/26/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class SourceCodeGraphLanguageYamlValueTypesTest {

    private static final String SOURCE_CODE = """
        source:
          uid: value-types-test-1.0
          variables:
            inline:
              mh.hyper-params:
                seed: 42
                enabled: true
                RNN: 'LSTM'
          processes:
            - code: proc-1
              name: proc 1
              function:
                code: fn-1
              metas:
                - upper-case-first-char: true
                - max: 7
                - prefix: sub
        version: 1
        """;

    private static SourceCodeGraph parse() {
        return SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.yaml, SOURCE_CODE);
    }

    private static Object meta(ExecContextParams.Process p, String key) {
        for (Map<String, String> m : p.metas) {
            final Object o = ((Map<?, ?>) m).get(key);
            if (o != null) {
                return o;
            }
        }
        return fail("meta not found: " + key);
    }

    private static ExecContextParams.Process proc1(SourceCodeGraph graph) {
        return graph.processes.stream().filter(o -> "proc-1".equals(o.processCode)).findFirst().orElseThrow();
    }

    @Test
    public void test_processMetaValues() {
        final ExecContextParams.Process p = proc1(parse());
        // Green-1: characterization of the CURRENT behaviour - yaml scalars keep their yaml type
        assertEquals("true", meta(p, "upper-case-first-char"));
        assertEquals("7", meta(p, "max"));
        assertEquals("sub", meta(p, "prefix"));
    }

    @Test
    public void test_inlineValues() {
        final Map<?, ?> hp = parse().variables.inline.get("mh.hyper-params");
        assertNotNull(hp);
        // Green-1: characterization of the CURRENT behaviour - yaml scalars keep their yaml type
        assertEquals("42", hp.get("seed"));
        assertEquals("true", hp.get("enabled"));
        assertEquals("LSTM", hp.get("RNN"));
    }
}
