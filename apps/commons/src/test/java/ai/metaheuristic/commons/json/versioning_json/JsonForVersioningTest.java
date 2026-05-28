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

import ai.metaheuristic.api.data.ParamsVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JsonForVersioning#getParamsVersion(String)}.
 *
 * <p>Regression suite for the pre-fix infinite-loop bug: the prior
 * implementation called {@code skipChildren()} on the top-level
 * {@code START_OBJECT} token, consuming the entire object and then looping
 * forever past {@code END_OBJECT}. The fix walks the token stream
 * iterating only on {@code FIELD_NAME} tokens.
 */
@Execution(ExecutionMode.CONCURRENT)
class JsonForVersioningTest {

    @Test
    void test_versionFirst() {
        ParamsVersion v = JsonForVersioning.getParamsVersion(
            "{\"version\":1,\"a\":1}");
        assertThat(v.getActualVersion()).isEqualTo(1);
    }

    @Test
    void test_versionInMiddle() {
        ParamsVersion v = JsonForVersioning.getParamsVersion(
            "{\"a\":1,\"version\":2,\"b\":2}");
        assertThat(v.getActualVersion()).isEqualTo(2);
    }

    @Test
    void test_versionLast() {
        ParamsVersion v = JsonForVersioning.getParamsVersion(
            "{\"a\":1,\"b\":2,\"version\":3}");
        assertThat(v.getActualVersion()).isEqualTo(3);
    }

    @Test
    void test_versionAfterNestedObject() {
        // The nested object must not be re-entered to look for "version".
        ParamsVersion v = JsonForVersioning.getParamsVersion(
            "{\"a\":{\"x\":1,\"y\":2},\"version\":4}");
        assertThat(v.getActualVersion()).isEqualTo(4);
    }

    @Test
    void test_versionAfterArray() {
        ParamsVersion v = JsonForVersioning.getParamsVersion(
            "{\"a\":[1,2,3],\"version\":5}");
        assertThat(v.getActualVersion()).isEqualTo(5);
    }

    @Test
    void test_nestedVersionIsIgnored() {
        // A "version" inside a nested object is not the top-level version.
        // The top-level "version":3 wins.
        ParamsVersion v = JsonForVersioning.getParamsVersion(
            "{\"a\":{\"version\":99},\"version\":3}");
        assertThat(v.getActualVersion()).isEqualTo(3);
    }

    @Test
    void test_onlyNestedVersion_doesNotLeakToTopLevel() {
        // The only "version" present is nested inside another object. The
        // top-level has no version field, so the default (1) must be
        // returned — NOT the nested 99. This was the load-bearing case
        // for the top-level-only requirement.
        ParamsVersion v = JsonForVersioning.getParamsVersion(
            "{\"a\":{\"version\":99}}");
        assertThat(v.getActualVersion()).isEqualTo(1);
    }

    @Test
    void test_versionInsideArray_doesNotLeak() {
        // "version" inside an array element is not the top-level version.
        ParamsVersion v = JsonForVersioning.getParamsVersion(
            "{\"items\":[{\"version\":77},{\"version\":88}]}");
        assertThat(v.getActualVersion()).isEqualTo(1);
    }

    @Test
    void test_deeplyNestedVersion_doesNotLeak() {
        // Top-level has no version; a deeply nested "version" field must
        // not be mistaken for it.
        ParamsVersion v = JsonForVersioning.getParamsVersion(
            "{\"a\":{\"b\":{\"c\":{\"version\":42}}}}");
        assertThat(v.getActualVersion()).isEqualTo(1);
    }

    @Test
    void test_missingVersion_defaultsToOne() {
        ParamsVersion v = JsonForVersioning.getParamsVersion(
            "{\"a\":1,\"b\":2}");
        assertThat(v.getActualVersion()).isEqualTo(1);
    }

    @Test
    void test_emptyObject_defaultsToOne() {
        ParamsVersion v = JsonForVersioning.getParamsVersion("{}");
        assertThat(v.getActualVersion()).isEqualTo(1);
    }

    @Test
    void test_versionTypeNotInt_defaultsToOne() {
        // The contract requires a numeric version; a string "version" is
        // ignored, falling back to the default.
        ParamsVersion v = JsonForVersioning.getParamsVersion(
            "{\"version\":\"two\"}");
        assertThat(v.getActualVersion()).isEqualTo(1);
    }
}
