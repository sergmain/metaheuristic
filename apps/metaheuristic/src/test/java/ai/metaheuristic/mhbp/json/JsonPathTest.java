/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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


    @Test
    public void test_() throws IOException {
        String json = """
                {
                  "created": 1687060833,
                  "data": [
                    {
                      "b64_json": "iVBOR"
                    }
                  ]
                }
                """;

        String jsonpath = "$['data'][0]['b64_json']";


        DocumentContext jsonContext = JsonPath.parse(json);
        String content = jsonContext.read(jsonpath);

        assertEquals("iVBOR", content);
    }

    // https://github.com/json-path/JsonPath
    // https://www.baeldung.com/guide-to-jayway-jsonpath
    // https://goessner.net/articles/JsonPath/
    @Test
    public void test_jsonPath() throws IOException {
        String json = IOUtils.resourceToString("/mhbp/openai/openai-response-01.json", StandardCharsets.UTF_8);

        String jsonpath = "$['choices'][0]['message']['content']";

        DocumentContext jsonContext = JsonPath.parse(json);
        String content = jsonContext.read(jsonpath);

        assertEquals("\n\nThis is a test!", content);
    }

    @Test
    public void test_jsonPath_2() throws IOException {
        String json = IOUtils.resourceToString("/mhbp/openai/2_plus_2_answer-2.json", StandardCharsets.UTF_8);

        String jsonpath = "$['choices'][0]['text']";

        DocumentContext jsonContext = JsonPath.parse(json);
        String content = jsonContext.read(jsonpath);

        assertEquals("\n\n4", content);
    }

}
