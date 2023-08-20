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

package ai.metaheuristic.commons.json.versioning_json;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.data.ParamsVersion;
import ai.metaheuristic.commons.exceptions.ParamsProcessingException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;

/**
 * @author Serge
 * Date: 4/16/2021
 * Time: 5:38 PM
 */
public class JsonForVersioning {

    // https://stackoverflow.com/questions/38732849/how-to-read-single-json-field-with-jackson/38733637#38733637
    public static ParamsVersion getParamsVersion(String json) {
        try {
            String versionValue = null;
            JsonFactory jsonFactory = new JsonFactory();
            try (JsonParser parser = jsonFactory.createParser(json)) {
                JsonToken startToken = parser.nextToken();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = parser.getCurrentName();
                    if ("version".equals(fieldName)) {
                        if (parser.nextToken() == JsonToken.VALUE_NUMBER_INT) {
                            versionValue = parser.getValueAsString();
                            break;
                        }
                    } else {
                        parser.skipChildren();
                    }
                }
            }

            return versionValue==null ? ConstsApi.PARAMS_VERSION_1 : new ParamsVersion(Integer.valueOf(versionValue));
        }
        catch (IOException e) {
            throw new ParamsProcessingException("Error: " + e.getMessage(), e);
        }
    }

}
