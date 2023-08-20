/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.mhbp.openai;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.utils.RestUtils;
import lombok.AllArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.fluent.Content;
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
public class QueryOpenaiTest {

    @AllArgsConstructor
    public static class Cfg {
        @Nullable
        String url;
        String prompt;

        public Cfg(String prompt) {
            this.prompt = prompt;
        }
    }

    Cfg json = new Cfg("""
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
            """);

    Cfg json1 = new Cfg("""
            {
              "model": "text-davinci-003",
              "prompt": "answer 2+2 with only digit"
            }
            """);

    Cfg json2 = new Cfg("""
            {
              "model": "text-davinci-003",
              "prompt": "answer square root of 9 with only digits"
            }
            """);

    Cfg json3 = new Cfg("""
            {
              "model": "text-davinci-003",
              "prompt": "List of fruits which can be grown in US. Output only name of fruit, put each name on new line,max 2 fruits"
            }
            """);
    Cfg json4 = new Cfg("""
            {
              "model": "text-davinci-003",
              "prompt": "List 2 fruits which can be grown in US and also include Orange in the list. Output only name of fruit, put each name on new line"
            }
            """);
    Cfg json5 = new Cfg(
            "https://api.openai.com/v1/chat/completions",
        """
                {
                  "model": "gpt-3.5-turbo",
                  "messages": [{"role": "system", "content": "Follow instructions as is without additions."},
                  {"role": "user", "content": "List 2 fruits which can be grown in US and also include Orange in the list. Output only name of fruit, put each name on new line"}],
                  "temperature": 0.9,
                  "max_tokens": 150,
                  "top_p": 1,
                  "frequency_penalty": 0,
                  "presence_penalty": 0.6,
                  "stop": [" Human:", " AI:"]
                }
                """);
//    {
//        "model": "text-davinci-003",
//            "prompt": "List 2 fruits which can be grown in US and also include Orange in the list. Output only name of fruit, put each name on new line"
//    }
    @Test
    public void test_query() throws URISyntaxException, IOException {
        String key = System.getenv(Consts.OPENAI_API_KEY);

        Cfg cfg = json5;

        final String url = cfg.url==null ? "https://api.openai.com/v1/completions" : cfg.url;
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

        String key = System.getenv(Consts.OPENAI_API_KEY);
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
