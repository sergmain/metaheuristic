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

package ai.metaheuristic.mhbp.openai;

import ai.metaheuristic.ai.utils.RestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.http.entity.StringEntity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * @author Sergio Lissner
 * Date: 4/13/2023
 * Time: 9:18 PM
 */
public class QueryOpenaiTest {

    @Test
    public void test_query() throws URISyntaxException, IOException {

        String key = System.getenv("OPENAI_API_KEY");
        String json = """
            {
              "model": "text-davinci-003",
              "prompt": "answer 2+2 with only digit",
              "temperature": 0.9,
              "max_tokens": 150,
              "top_p": 1,
              "frequency_penalty": 0,
              "presence_penalty": 0.6,
              "stop": [" Human:", " AI:"]
            }
            """;

        String json1 = """
            {
              "model": "text-davinci-003",
              "prompt": "answer 2+2 with only digit"
            }
            """;

        String json2 = """
            {
              "model": "text-davinci-003",
              "prompt": "answer square root of 9 with only digits"
            }
            """;

        String json3 = """
            {
              "model": "text-davinci-003",
              "prompt": "List of fruits which can be grown in US. Output only name of fruit, put each name on new line,max 2 fruits"
            }
            """;

        final URI uri = new URIBuilder("https://api.openai.com/v1/completions")
                .setCharset(StandardCharsets.UTF_8)
                .build();
        final Request request = Request.Post(uri).connectTimeout(Timeout.ofSeconds(5)).socketTimeout(20000);

        request.body(new StringEntity(json3, StandardCharsets.UTF_8));

        RestUtils.addHeaders(request);
        request.addHeader("Content-Type", "application/json");
        request.addHeader("Authorization", "Bearer " + key);
        final Executor executor = Executor.newInstance();

        Response response = executor.execute(request);

        final HttpResponse httpResponse = response.returnResponse();
        final HttpEntity entity = httpResponse.getEntity();
        final int statusCode = httpResponse.getCode();
        System.out.println("statusCode: " + statusCode);
        System.out.println("entity: " + IOUtils.toString(entity.getContent(), StandardCharsets.UTF_8));
    }

    @Test
    public void test_models() throws URISyntaxException, IOException {

        String key = System.getenv("OPENAI_API_KEY");
        final URI uri = new URIBuilder("https://api.openai.com/v1/models")
                .setCharset(StandardCharsets.UTF_8)
                .build();
        final Request request = Request.get(uri).connectTimeout(Timeout.ofSeconds(5)).socketTimeout(20000);

        request.addHeader("Authorization", "Bearer " + key);
        final Executor executor = Executor.newInstance();

        Response response = executor.execute(request);

        final HttpResponse httpResponse = response.returnResponse();
        final HttpEntity entity = httpResponse.getEntity();
        final int statusCode = httpResponse.getCode();
        System.out.println("statusCode: " + statusCode);
        System.out.println("entity: " + IOUtils.toString(entity.getContent(), StandardCharsets.UTF_8));
    }
}
