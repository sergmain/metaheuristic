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

package ai.metaheuristic.mhbp.json;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Sergio Lissner
 * Date: 4/12/2023
 * Time: 9:00 PM
 */
public class JsonPathTest {

    // https://github.com/json-path/JsonPath
    // https://www.baeldung.com/guide-to-jayway-jsonpath
    // https://goessner.net/articles/JsonPath/
    @Test
    public void test_jsonPath() throws IOException {
        String json = IOUtils.resourceToString("/openai/openai-response-01.json", StandardCharsets.UTF_8);

        String jsonpath = "$['choices'][0]['message']['content']";

        DocumentContext jsonContext = JsonPath.parse(json);
        String content = jsonContext.read(jsonpath);

        assertEquals("\n\nThis is a test!", content);
    }

    @Test
    public void test_jsonPath_2() throws IOException {
        String json = IOUtils.resourceToString("/openai/2_plus_2_answer-2.json", StandardCharsets.UTF_8);

        String jsonpath = "$['choices'][0]['text']";

        DocumentContext jsonContext = JsonPath.parse(json);
        String content = jsonContext.read(jsonpath);

        assertEquals("\n\n4", content);
    }

}
