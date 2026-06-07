/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.JacksonException;

import java.io.IOException;

/**
 * Fast extraction of the <b>top-level</b> {@code "version"} field from a JSON
 * document, without building a full object tree.
 *
 * <p>The lexer walks the token stream of the root object and skips any nested
 * objects/arrays entirely. A {@code "version"} field nested inside another
 * object is not the top-level version and is not returned. If no top-level
 * {@code "version"} field is present, version 1 is the default.
 *
 * <p>The input is assumed to be a well-formed JSON object serialized by this
 * codebase — no defensive parsing of pathological inputs. Broken JSON is the
 * caller's responsibility.
 *
 * @author Serge
 * Date: 4/16/2021
 * Time: 5:38 PM
 */
public class JsonForVersioning {

    public static ParamsVersion getParamsVersion(String json) {
        try {
            String versionValue = null;
            JsonFactory jsonFactory = new JsonFactory();
            try (JsonParser parser = jsonFactory.createParser(json)) {
                // First token is the root START_OBJECT.
                if (parser.nextToken() != JsonToken.START_OBJECT) {
                    return ConstsApi.PARAMS_VERSION_1;
                }
                // Walk only the direct children of the root object. On each
                // FIELD_NAME, check the key; if it's "version", read the next
                // numeric token; otherwise consume the field's value subtree
                // and continue at depth 1.
                JsonToken token;
                while ((token = parser.nextToken()) != null && token != JsonToken.END_OBJECT) {
                    if (token != JsonToken.PROPERTY_NAME) {
                        // Shouldn't happen for well-formed JSON inside an
                        // object — every child position starts with a
                        // FIELD_NAME. Skip defensively.
                        continue;
                    }
                    String fieldName = parser.currentName();
                    JsonToken valueToken = parser.nextToken();
                    if ("version".equals(fieldName) && valueToken == JsonToken.VALUE_NUMBER_INT) {
                        versionValue = parser.getValueAsString();
                        break;
                    }
                    // Consume this field's value entirely. For scalar tokens
                    // skipChildren() is a no-op; for START_OBJECT /
                    // START_ARRAY it advances to the matching END_*, leaving
                    // the parser ready for the next FIELD_NAME at depth 1.
                    parser.skipChildren();
                }
            }

            return versionValue == null ? ConstsApi.PARAMS_VERSION_1 : new ParamsVersion(Integer.valueOf(versionValue));
        }
        catch (JacksonException e) {
            throw new ParamsProcessingException("Error: " + e, e);
        }
    }

}
