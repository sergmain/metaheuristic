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

package ai.metaheuristic.ai.utils;

import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.commons.utils.JsonUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;

import tools.jackson.core.JacksonException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Sergio Lissner
 * Date: 6/24/2023
 * Time: 1:47 AM
 */
@Execution(ExecutionMode.CONCURRENT)
public class JsonUtilsTest {

    public record SimpleItem(int id, String name) {}

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleList extends BaseDataClass {
        public List<SimpleItem> items;

        @JsonCreator
        public SimpleList(@JsonProperty("items") List<SimpleItem> items,
                          @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
                          @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.items = items;
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }
    }

    public static final SimpleList SIMPLE_LIST;

    public static final String INFO_MESSAGE = "This is a simple list";

    static {
        SIMPLE_LIST = new SimpleList(
                List.of(
                        new SimpleItem(1, "Item #1"),
                        new SimpleItem(2, "Item #2"),
                        new SimpleItem(3, "Item #3"),
                        new SimpleItem(4, "Item #4"),
                        new SimpleItem(5, "Item #5")
                )
        );
        SIMPLE_LIST.addInfoMessage(INFO_MESSAGE);
    }

    @Test
    public void test_() {


        String json = JsonUtils.getMapper().writeValueAsString(SIMPLE_LIST);
        System.out.println(json);

        assertTrue(json.contains(INFO_MESSAGE));
    }

    // --- Characterization tests: JsonUtils.getMapper() Jackson-3 leniency contract ---

    @Test
    public void test_getMapper_toleratesTrailingTokens() {
        // Jackson 3.x flipped FAIL_ON_TRAILING_TOKENS default to true; JsonUtils disables it
        // to keep the Jackson 2.x contract, so trailing junk after the first value is ignored.
        SimpleItem item = JsonUtils.getMapper().readValue("{\"id\":7,\"name\":\"seven\"}\";", SimpleItem.class);
        assertEquals(7, item.id());
        assertEquals("seven", item.name());
    }

    @Test
    public void test_getMapper_ignoresUnknownProperties() {
        // FAIL_ON_UNKNOWN_PROPERTIES is disabled: unexpected fields are skipped, not fatal.
        SimpleItem item = JsonUtils.getMapper().readValue("{\"id\":3,\"name\":\"three\",\"extra\":true}", SimpleItem.class);
        assertEquals(3, item.id());
        assertEquals("three", item.name());
    }

    @Test
    public void test_getMapper_failsOnNullForMissingPrimitive() {
        // Characterizes the one Jackson-3 default NOT restored on the central mapper:
        // FAIL_ON_NULL_FOR_PRIMITIVES stays true, so an omitted primitive (int id) throws.
        // If that feature is ever disabled on JsonUtils, this test will go red on purpose.
        assertThrows(JacksonException.class,
                () -> JsonUtils.getMapper().readValue("{\"name\":\"no-id\"}", SimpleItem.class));
    }
}
