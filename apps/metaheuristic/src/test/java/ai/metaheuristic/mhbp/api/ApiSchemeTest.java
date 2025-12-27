/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.mhbp.api;

import ai.metaheuristic.commons.yaml.scheme.ApiScheme;
import ai.metaheuristic.commons.yaml.scheme.ApiSchemeUtils;
import ai.metaheuristic.api.EnumsApi;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static ai.metaheuristic.api.EnumsApi.HttpMethodType.get;
import static ai.metaheuristic.api.EnumsApi.HttpMethodType.post;
import static ai.metaheuristic.api.EnumsApi.PromptPlace.uri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Sergio Lissner
 * Date: 4/12/2023
 * Time: 10:16 PM
 */
@Execution(CONCURRENT)
public class ApiSchemeTest {

    @Test
    public void test_openai() throws IOException {
        String yaml  = IOUtils.resourceToString("/mhbp/mh-apis/mh-openai-text-davinci-003.yaml", StandardCharsets.UTF_8);

        ApiScheme as = ApiSchemeUtils.UTILS.to(yaml);

        assertEquals("mh.openai-davinci-003:1.0", as.code);
        assertEquals("mh.openai", as.scheme.auth.code);
        assertEquals(post, as.scheme.request.type);
        assertEquals("https://api.openai.com/v1/completions", as.scheme.request.uri);
        assertNotNull(as.scheme.request.prompt);
        final EnumsApi.PromptPlace place = as.scheme.request.prompt.place;
        assertEquals(EnumsApi.PromptPlace.text, place);
        assertEquals("$prompt$", as.scheme.request.prompt.replace);
        assertEquals("""
                {
                  "model": "text-davinci-003",
                  "prompt": "$prompt$",
                  "temperature": 0.9,
                  "max_tokens": 150,
                  "top_p": 1,
                  "frequency_penalty": 0,
                  "presence_penalty": 0.6,
                  "stop": [" Human:", " AI:"]
                }
                """, as.scheme.request.prompt.text);

        assertEquals(EnumsApi.PromptResponseType.json, as.scheme.response.type);
        assertEquals("$['choices'][0]['message']['content']", as.scheme.response.path);
    }

    @Test
    public void test_simple() throws IOException {
        String yaml  = IOUtils.resourceToString("/mhbp/mh-apis/mh-simple.yaml", StandardCharsets.UTF_8);

        ApiScheme as = ApiSchemeUtils.UTILS.to(yaml);

        assertEquals("mh.simple-localhost:1.0", as.code);
        assertEquals("mh.simple", as.scheme.auth.code);
        assertEquals(get, as.scheme.request.type);
        assertEquals("http://localhost:8080/rest/v1/provider/simple/stub/question", as.scheme.request.uri);
        assertNotNull(as.scheme.request.prompt);
        assertEquals(uri, as.scheme.request.prompt.place);
        assertEquals("q", as.scheme.request.prompt.param);

        assertEquals(EnumsApi.PromptResponseType.text, as.scheme.response.type);
    }
}
