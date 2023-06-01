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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.mhbp.data.ApiData;
import ai.metaheuristic.ai.mhbp.yaml.scheme.ApiScheme;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

/**
 * @author Sergio Lissner
 * Date: 3/19/2023
 * Time: 10:07 PM
 */
public class ProviderQueryUtils {

    public static ApiData.RawAnswerFromAPI processAnswerFromApi(String json, ApiScheme.Response response) {
        if (response.type==Enums.PromptResponseType.text) {
            return new ApiData.RawAnswerFromAPI(response.type, json);
        }
        if (response.type==Enums.PromptResponseType.json) {
            DocumentContext jsonContext = JsonPath.parse(json);
            String content = jsonContext.read(response.path);
            return new ApiData.RawAnswerFromAPI(response.type, content);
        }
        if (response.type==Enums.PromptResponseType.image) {
            DocumentContext jsonContext = JsonPath.parse(json);
            String content = jsonContext.read(response.path);
            return new ApiData.RawAnswerFromAPI(response.type, content);
        }
        throw new IllegalStateException();
    }

}
