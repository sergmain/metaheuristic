/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JsonUtils#isValidJson} - IO-free static logic, so plain JUnit with no Spring context and no
 * doubles of any kind. The function under test IS the assertion subject; there is nothing to
 * impersonate.
 *
 * @author Serge
 */
@Execution(ExecutionMode.CONCURRENT)
public class JsonUtilsTest {

    @Test
    public void test_isValidJson_wellFormed() {
        assertTrue(JsonUtils.isValidJson("{}"), "empty object");
        assertTrue(JsonUtils.isValidJson("[]"), "empty array");
        assertTrue(JsonUtils.isValidJson("{\"a\":1,\"b\":\"x\"}"), "flat object");
        assertTrue(JsonUtils.isValidJson("{\"a\":{\"b\":[1,2]},\"c\":null}"), "nested object and array");
        assertTrue(JsonUtils.isValidJson("  \n {\"a\":1} \t "), "surrounding whitespace is not content");
        assertTrue(JsonUtils.isValidJson("{\"a\":\"\\u0041\\n\\t\\\"\\\\\"}"), "valid escape sequences");
        assertTrue(JsonUtils.isValidJson("{\"a\":-1.5e10,\"b\":true,\"c\":false}"), "numbers and literals");
        assertTrue(JsonUtils.isValidJson("{\"кир\":\"текст\"}"), "non-ASCII property names and values");
        assertTrue(JsonUtils.isValidJson("[[[1]]]"), "nested arrays close back to depth 0 exactly once");
        assertTrue(JsonUtils.isValidJson("{\"a\":[{\"b\":[]}]}"), "objects and arrays interleaved");
    }

    @Test
    public void test_isValidJson_malformed() {
        assertFalse(JsonUtils.isValidJson("{\"a\":1"), "unclosed object");
        assertFalse(JsonUtils.isValidJson("{\"a\":[1,2}]}"), "mismatched closing token");
        assertFalse(JsonUtils.isValidJson("{\"a\":\"bad \\x esc\"}"), "invalid escape - caught while skipping, not decoding");
        assertFalse(JsonUtils.isValidJson("{\"a\":01}"), "leading-zero number");
        assertFalse(JsonUtils.isValidJson("{\"a\":NaN}"), "bare NaN is not JSON");
        assertFalse(JsonUtils.isValidJson("{'a':1}"), "single-quoted property name");
        assertFalse(JsonUtils.isValidJson("{\"a\":1,}"), "trailing comma");
        assertFalse(JsonUtils.isValidJson("just a plain string, not JSON at all"), "unquoted prose");
    }

    @Test
    public void test_isValidJson_trailingContentIsRejected() {
        // nextToken() returns null at end of INPUT, not at the end of the first complete value,
        // so anything after a finished document is seen and rejected.
        assertFalse(JsonUtils.isValidJson("{\"a\":1} oops"), "garbage after a complete object");
        // ❗ Jackson accepts a root-level value SEQUENCE by default, so a bare token walk returns
        // true for all three of these. The rootDone guard in isValidJson is what rejects them, and
        // it matters: getMapper() sets FAIL_ON_TRAILING_TOKENS=false, so readValue would silently
        // take the first document and discard the rest.
        assertFalse(JsonUtils.isValidJson("{\"a\":1}{\"b\":2}"), "two concatenated objects");
        assertFalse(JsonUtils.isValidJson("[1,2] [3]"), "two concatenated arrays");
        assertFalse(JsonUtils.isValidJson("42 43"), "two concatenated numbers");
        assertFalse(JsonUtils.isValidJson("\"a\" \"b\""), "two concatenated strings");
    }

    @Test
    public void test_isValidJson_nullAndBlank() {
        assertFalse(JsonUtils.isValidJson(null), "null is not JSON");
        assertFalse(JsonUtils.isValidJson(""), "empty string is not JSON");
        assertFalse(JsonUtils.isValidJson("   \n\t "), "blank string is not JSON");
    }

    @Test
    public void test_isValidJson_wellFormedIsNotWellShaped() {
        // ⚠️ Pinning what this method deliberately does NOT do, so a caller needing more knows to
        // check it themselves rather than assuming this covers it.
        assertTrue(JsonUtils.isValidJson("42"), "a bare number is a valid JSON document");
        assertTrue(JsonUtils.isValidJson("\"hello\""), "a bare string is a valid JSON document");
        assertTrue(JsonUtils.isValidJson("null"), "a bare null is a valid JSON document");
        assertTrue(JsonUtils.isValidJson("{\"a\":1,\"a\":2}"), "duplicate property names are permitted by the JSON grammar");
    }

    @Test
    public void test_isValidJson_nestingDepthIsBounded() {
        // StreamReadConstraints caps nesting depth at 500 by default. Exceeding it throws a
        // StreamConstraintsException, which is a JacksonException, so it is reported as invalid.
        // ⚠️ That conflates "not JSON" with "JSON but too deep" - desirable at a trust boundary,
        // misleading for a legitimate deep document. This test is what makes the limit visible.
        assertTrue(JsonUtils.isValidJson(nest(100)), "depth 100 is well within the limit");
        assertFalse(JsonUtils.isValidJson(nest(1000)), "depth 1000 exceeds the default max nesting depth");
    }

    @Test
    public void test_isValidJson_largeBody() {
        // The size the meta storage assumes a record body stays under.
        final StringBuilder sb = new StringBuilder("{\"items\":[");
        for (int i = 0; i < 500; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":").append(i).append(",\"email\":\"u").append(i).append("@example.com\"}");
        }
        sb.append("]}");
        final String big = sb.toString();
        assertTrue(big.length() > 16_000, "the fixture must actually exceed 16k, was " + big.length());
        assertTrue(JsonUtils.isValidJson(big), "a well-formed 16k+ body");

        // truncating anywhere inside must fail
        assertFalse(JsonUtils.isValidJson(big.substring(0, big.length() - 1)), "truncated by one char");
        assertFalse(JsonUtils.isValidJson(big.substring(0, big.length() / 2)), "truncated in half");
    }

    private static String nest(int depth) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append("{\"a\":");
        }
        sb.append('1');
        for (int i = 0; i < depth; i++) {
            sb.append('}');
        }
        return sb.toString();
    }
}
