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

package ai.metaheuristic.ai.mhbp.provider;

import ai.metaheuristic.ai.mhbp.data.ApiData;
import ai.metaheuristic.commons.yaml.scheme.ApiScheme;
import ai.metaheuristic.api.EnumsApi;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.apache.commons.codec.binary.Base64;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * @author Sergio Lissner
 * Date: 3/19/2023
 * Time: 10:07 PM
 */
public class ProviderQueryUtils {

    public static ApiData.ProcessedAnswerFromAPI processAnswerFromApi(ApiData.RawAnswerFromAPI rawAnswerFromAPI, ApiScheme.Response response) {
        if (response.type== EnumsApi.PromptResponseType.text) {
            Objects.requireNonNull(rawAnswerFromAPI.raw());
            return new ApiData.ProcessedAnswerFromAPI(rawAnswerFromAPI, rawAnswerFromAPI.raw());
        }
        if (response.type== EnumsApi.PromptResponseType.json) {
            Objects.requireNonNull(rawAnswerFromAPI.raw());
            DocumentContext jsonContext = JsonPath.parse(rawAnswerFromAPI.raw());
            String content = jsonContext.read(response.path);
            return new ApiData.ProcessedAnswerFromAPI(rawAnswerFromAPI, content);
        }
        if (response.type== EnumsApi.PromptResponseType.image) {
            Objects.requireNonNull(rawAnswerFromAPI.bytes());
            return new ApiData.ProcessedAnswerFromAPI(rawAnswerFromAPI, null);
        }
        if (response.type== EnumsApi.PromptResponseType.image_base64) {
            Objects.requireNonNull(rawAnswerFromAPI.bytes());
            String base64 = new String(rawAnswerFromAPI.bytes(), StandardCharsets.UTF_8);
            DocumentContext jsonContext = JsonPath.parse(base64);
            String content = jsonContext.read(response.path);

            byte[] actualBytes = Base64.decodeBase64(content);

            final ApiData.ProcessedAnswerFromAPI result = new ApiData.ProcessedAnswerFromAPI(new ApiData.RawAnswerFromAPI(response.type, actualBytes), null);
            return result;
        }
        throw new IllegalStateException();
    }

}
