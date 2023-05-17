/*
 *    Copyright 2023, Sergio Lissner, Innovation platforms, LLC
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package ai.metaheuristic.mhbp.api;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.mhbp.yaml.scheme.ApiScheme;
import ai.metaheuristic.ai.mhbp.yaml.scheme.ApiSchemeUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static ai.metaheuristic.ai.Enums.HttpMethodType.get;
import static ai.metaheuristic.ai.Enums.HttpMethodType.post;
import static ai.metaheuristic.ai.Enums.PromptPlace.uri;
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
        String yaml  = IOUtils.resourceToString("/mhbp/api/openai-provider.yaml", StandardCharsets.UTF_8);

        ApiScheme as = ApiSchemeUtils.UTILS.to(yaml);

        assertEquals("openai-provider:1.0", as.code);
        assertEquals("openai", as.scheme.auth.code);
        assertEquals(post, as.scheme.request.type);
        assertEquals("https://api.openai.com/v1/completions", as.scheme.request.uri);
        assertNotNull(as.scheme.request.prompt);
        final Enums.PromptPlace place = as.scheme.request.prompt.place;
        assertEquals(Enums.PromptPlace.text, place);
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

        assertEquals(Enums.PromptResponseType.json, as.scheme.response.type);
        assertEquals("$['choices'][0]['message']['content']", as.scheme.response.path);
    }

    @Test
    public void test_simple() throws IOException {
        String yaml  = IOUtils.resourceToString("/mhbp/api/simple-provider.yaml", StandardCharsets.UTF_8);

        ApiScheme as = ApiSchemeUtils.UTILS.to(yaml);

        assertEquals("simple-provider-localhost:1.0", as.code);
        assertEquals("simple", as.scheme.auth.code);
        assertEquals(get, as.scheme.request.type);
        assertEquals("http://localhost:8080/rest/v1/provider/simple/stub/question", as.scheme.request.uri);
        assertNotNull(as.scheme.request.prompt);
        assertEquals(uri, as.scheme.request.prompt.place);
        assertEquals("q", as.scheme.request.prompt.param);

        assertEquals(Enums.PromptResponseType.text, as.scheme.response.type);
    }
}
