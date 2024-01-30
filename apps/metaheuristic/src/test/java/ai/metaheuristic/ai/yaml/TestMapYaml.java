/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml;

import ai.metaheuristic.commons.yaml.YamlUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.yaml.snakeyaml.Yaml;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Serge
 * Date: 2/1/2020
 * Time: 12:24 AM
 */
@Execution(CONCURRENT)
public class TestMapYaml {

    private static final String Y = """
            variables:
              inline:
                mh.hyper-params:
                  RNN: [LSTM, GRU]
                  batches: '40'
                  seed: '42'
                  time_steps: '[15,30]'
            """;
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableDefinitionV8 {
        public String global;
        public final Map<String, Map<String, String>> inline = new HashMap<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Main {
        public VariableDefinitionV8 variables;
    }

    @Test
    public void test() {
        Yaml yaml = YamlUtils.init(Main.class);
        final Main m = yaml.load(Y);

        System.out.println(m);

        String str = yaml.dump(m);
        System.out.println(str);
    }

    @Data
    @NoArgsConstructor
    public static class MapOfPairs {
        public final Map<String, String> metas = new HashMap<>();
    }

    private static final String Y_02 = """
            metas:
                  RNN: '[LSTM, GRU]'
                  batches: '40'
                  seed: '42'
                  time_steps: '[15,30]'
            """;

    @Test
    public void test_02() {
        Yaml yaml = YamlUtils.init(MapOfPairs.class);
        final MapOfPairs m = yaml.load(Y_02);

        System.out.println(m);

        String str = yaml.dump(m);
        System.out.println(str);
    }



}
