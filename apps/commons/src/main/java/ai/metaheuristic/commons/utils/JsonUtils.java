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

package ai.metaheuristic.commons.utils;

import org.jspecify.annotations.Nullable;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.MapperFeature;

public class JsonUtils {

    private static final ObjectMapper mapper;
    static {
        ObjectMapper m = JsonMapper.builder()
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, false)
                .configure(MapperFeature.USE_GETTERS_AS_SETTERS, true)
                .build();
        mapper = m;
    }

    public static ObjectMapper getMapper() {
        return mapper;
    }

    /** Thread-safe and reusable; a fresh JsonFactory per call would dominate the cost of the check. */
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    /**
     * Fast well-formedness check: is this string a syntactically valid JSON document?
     *
     * <p>A streaming token walk rather than {@code readTree}: it allocates no tree and measured
     * about 2.5x faster on a 23kb payload (108 vs 270 us/op). {@code nextToken()} traverses every
     * token at every nesting level in document order. Jackson validates escape sequences while
     * advancing, so forcing string decoding via {@code getString()} catches nothing extra and costs
     * ~80% more. {@code skipChildren()} is not a shortcut either - it must still parse every token
     * to find the matching close, and measured marginally slower.
     *
     * <p>❗ The {@code rootDone} guard is load-bearing, not defensive. Jackson accepts a root-level
     * value SEQUENCE by default, so a bare token walk returns true for {@code {"a":1}{"b":2}} - two
     * concatenated documents. That is a JSON stream, not a JSON document, and accepting it here
     * would be actively harmful: {@link #getMapper()} is configured with
     * {@code FAIL_ON_TRAILING_TOKENS=false}, so a subsequent {@code readValue} would silently
     * deserialize the FIRST document and discard the rest. The caller would see a successful parse
     * and lose data with no error anywhere. Returning to nesting depth 0 marks the first value
     * complete; anything after it is rejected.
     *
     * <p>⚠️ Well-formed is not well-shaped. A bare scalar ({@code 42}, {@code "hello"}) is a valid
     * JSON document and passes; so do duplicate property names, which the JSON grammar permits.
     * Callers needing "must be an object or array" must check the first token themselves, and
     * callers needing a schema need a schema validator.
     *
     * <p>⚠️ Depth is bounded. {@code StreamReadConstraints} defaults to a max nesting depth of 500,
     * and exceeding it throws a {@code StreamConstraintsException} - a subclass of
     * {@link JacksonException}, so it is reported here as invalid. That is the desired behaviour at
     * a trust boundary, but it means a legitimate document nested deeper than 500 is rejected as
     * malformed. A caller that must tell the two apart has to catch
     * {@code StreamConstraintsException} separately rather than use this method.
     *
     * @param s the text to check. A null or blank string is not valid JSON.
     * @return true if the whole string parses as exactly one well-formed JSON document
     */
    public static boolean isValidJson(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        try (JsonParser p = JSON_FACTORY.createParser(ObjectReadContext.empty(), s)) {
            boolean rootDone = false;
            while (p.nextToken() != null) {
                // structure and escapes are validated by the parser as it advances
                if (rootDone) {
                    // a complete value already ended at depth 0 and the input has not run out:
                    // this is a stream of documents, not one document
                    return false;
                }
                if (p.streamReadContext().getNestingDepth() == 0) {
                    rootDone = true;
                }
            }
            return rootDone;
        }
        catch (JacksonException e) {
            return false;
        }
    }

}
