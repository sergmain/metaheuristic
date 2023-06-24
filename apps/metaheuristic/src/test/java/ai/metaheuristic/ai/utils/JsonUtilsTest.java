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

package ai.metaheuristic.ai.utils;

import ai.metaheuristic.api.data.BaseDataClass;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Sergio Lissner
 * Date: 6/24/2023
 * Time: 1:47 AM
 */
public class JsonUtilsTest {

    public record SimpleItem(int id, String name) {}

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleList extends BaseDataClass {
        public List<SimpleItem> items;
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
    public void test_() throws JsonProcessingException {


        String json = JsonUtils.getMapper().writeValueAsString(SIMPLE_LIST);
        System.out.println(json);

        assertTrue(json.contains(INFO_MESSAGE));
    }
}
