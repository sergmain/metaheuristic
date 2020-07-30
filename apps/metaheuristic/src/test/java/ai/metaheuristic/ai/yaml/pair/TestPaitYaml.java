/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.yaml.pair;

import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV1;
import ai.metaheuristic.commons.yaml.YamlUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Serge
 * Date: 7/30/2020
 * Time: 12:21 PM
 */
public class TestPaitYaml {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class WithPair {
        public String name;
        public List<MutablePair<String, String>> metas = new ArrayList<>();
    }

    @Test
    public void test() {
        WithPair withPair = new WithPair("aaa", List.of(new MutablePair<>("key", "value")));

        Yaml yaml = YamlUtils.init(WithPair.class);

        String s = yaml.dump(withPair);
        System.out.println(s);

        WithPair pair = yaml.load(s);

        assertEquals(withPair.name, pair.name);
        assertEquals(withPair.metas.size(), pair.metas.size());
        assertEquals(withPair.metas.get(0), pair.metas.get(0));

    }
}
