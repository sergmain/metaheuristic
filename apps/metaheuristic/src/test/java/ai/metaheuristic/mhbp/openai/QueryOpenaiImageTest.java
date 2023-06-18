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
import lombok.AllArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * @author Sergio Lissner
 * Date: 4/13/2023
 * Time: 9:18 PM
 */
@Disabled
public class QueryOpenaiImageTest {

    @AllArgsConstructor
    public static class Cfg {
        @Nullable
        String url;
        String prompt;

        public Cfg(String prompt) {
            this.prompt = prompt;
        }
    }

    Cfg json5 = new Cfg(
            "https://api.openai.com/v1/images/generations",
        """
                {
                     "prompt": "Some Oranges",
                     "n": 1,
                     "size": "256x256",
                     "response_format" : "b64_json"
                   }
                """);


/*
response = openai.Image.create(
    prompt=PROMPT,
    n=1,
    size="256x256",
    response_format="b64_json",
)

print(response["data"][0]["b64_json"][:50])
*/
/*
  "created": 1687058794,
  "data": [
    {
      "b64_json":
*/

    @Test
    public void test_query() throws URISyntaxException, IOException {
        String key = System.getenv("OPENAI_API_KEY");

        Cfg cfg = json5;

        final String url = cfg.url==null ? "https://api.openai.com/v1/completions" : cfg.url.strip();
        final URI uri = new URIBuilder(url)
                .setCharset(StandardCharsets.UTF_8)
                .build();
        final Request request = Request.post(uri).connectTimeout(Timeout.ofSeconds(5));//.socketTimeout(20000);

        request.body(new StringEntity(cfg.prompt, StandardCharsets.UTF_8));

        RestUtils.addHeaders(request);
        request.addHeader("Content-Type", "application/json");
        request.addHeader("Authorization", "Bearer " + key);
        final Executor executor = Executor.newInstance();

        Response response = executor.execute(request);

        final HttpResponse httpResponse = response.returnResponse();
        if (!(httpResponse instanceof ClassicHttpResponse classicHttpResponse)) {
            throw new IllegalStateException("(!(httpResponse instanceof ClassicHttpResponse classicHttpResponse))");
        }
        final int statusCode = classicHttpResponse.getCode();
        final HttpEntity entity = classicHttpResponse.getEntity();
        System.out.println("statusCode: " + statusCode);
        System.out.println("entity: " + IOUtils.toString(entity.getContent(), StandardCharsets.UTF_8));
    }

    @Test
    public void test_models() throws URISyntaxException, IOException {

        String key = System.getenv("OPENAI_API_KEY");
        final URI uri = new URIBuilder("https://api.openai.com/v1/models")
                .setCharset(StandardCharsets.UTF_8)
                .build();
        final Request request = Request.get(uri).connectTimeout(Timeout.ofSeconds(5));//.socketTimeout(20000);

        request.addHeader("Authorization", "Bearer " + key);
        final Executor executor = Executor.newInstance();

        Response response = executor.execute(request);

        final HttpResponse httpResponse = response.returnResponse();
        if (!(httpResponse instanceof ClassicHttpResponse classicHttpResponse)) {
            throw new IllegalStateException("(!(httpResponse instanceof ClassicHttpResponse classicHttpResponse))");
        }
        final int statusCode = classicHttpResponse.getCode();
        final HttpEntity entity = classicHttpResponse.getEntity();
        System.out.println("statusCode: " + statusCode);
        System.out.println("entity: " + IOUtils.toString(entity.getContent(), StandardCharsets.UTF_8));
    }
}
