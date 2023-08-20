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

import ai.metaheuristic.ai.mhbp.data.ApiData;
import ai.metaheuristic.ai.mhbp.yaml.scheme.ApiScheme;
import ai.metaheuristic.ai.mhbp.yaml.scheme.ApiSchemeUtils;
import ai.metaheuristic.ww2003.image.ImageUtils;
import com.google.common.base.CharMatcher;
import com.google.common.io.BaseEncoding;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Sergio Lissner
 * Date: 6/17/2023
 * Time: 9:17 PM
 */
public class OpenaiImageConverterTest {

    @Test
    public void test_(@TempDir Path temp) throws IOException {
        String json = IOUtils.resourceToString("/mhbp/openai/variable-103813-image_of_oranges.json", StandardCharsets.UTF_8);
        String apiYaml = IOUtils.resourceToString("/mhbp/api/openai-dall-e-256x256.yaml", StandardCharsets.UTF_8);

        ApiScheme apiScheme = ApiSchemeUtils.UTILS.to(apiYaml);

        DocumentContext jsonContext = JsonPath.parse(json);
        String content = jsonContext.read(apiScheme.scheme.response.path);

        byte[] actualBytes = Base64.decodeBase64(content.getBytes(StandardCharsets.UTF_8));
//        byte[] actualBytes = BaseEncoding.base64().decode(CharMatcher.whitespace().removeFrom(content));;

        String type = ImageUtils.getTypeAsStr(new ByteArrayInputStream(actualBytes));

        System.out.println(type);

        Path p = temp.resolve("image.png");
        Files.write(p, actualBytes);

        System.out.println(temp.toAbsolutePath());
        int i=0;
    }
}
