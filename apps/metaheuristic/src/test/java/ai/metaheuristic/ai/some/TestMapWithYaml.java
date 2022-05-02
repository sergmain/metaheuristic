/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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

package ai.metaheuristic.ai.some;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.commons.yaml.YamlUtils;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 5/1/2022
 * Time: 9:04 PM
 */
public class TestMapWithYaml {

    private static final String AGGREGATE_COMMAND_INSPECTION_1_5 = "aggregate-command-inspection:1.5";
    private static final String GET_LIST_OF_EDITION_PAIRS_1_1_7 = "get-list-of-edition-pairs:1.1.7";
    private static final String AAA_BBB = "aaa bbb";
    public static final String AS_NULL = "as-null";

    @Data
    @NoArgsConstructor
    public static class To {
        public static class Status {
            public Map<String, Enums.FunctionState> map = new HashMap<>();
        }
        public final Status status = new Status();
    }

    @Test
    public void test() {
        To to = new To();
        to.status.map.put(AGGREGATE_COMMAND_INSPECTION_1_5, Enums.FunctionState.ok);
        to.status.map.put(GET_LIST_OF_EDITION_PAIRS_1_1_7, Enums.FunctionState.asset_error);
        to.status.map.put(AAA_BBB, Enums.FunctionState.checksum_wrong);
        to.status.map.put(AS_NULL, null);

        Yaml y = YamlUtils.init(To.class);
        String yaml = y.dumpAsMap(to);
        System.out.println(yaml);

        To to1 = y.load(yaml);
        Map<String, Enums.FunctionState> m = to1.status.map;

        assertTrue(m.containsKey(AGGREGATE_COMMAND_INSPECTION_1_5));
        assertTrue(m.containsKey(GET_LIST_OF_EDITION_PAIRS_1_1_7));
        assertTrue(m.containsKey(AAA_BBB));
        assertTrue(m.containsKey(AS_NULL));
        assertEquals(Enums.FunctionState.ok, m.get(AGGREGATE_COMMAND_INSPECTION_1_5));
        assertEquals(Enums.FunctionState.asset_error, m.get(GET_LIST_OF_EDITION_PAIRS_1_1_7));
        assertEquals(Enums.FunctionState.checksum_wrong, m.get(AAA_BBB));
        assertTrue(m.containsKey(AS_NULL));
        assertNull(m.get(AS_NULL));

    }
}