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

package ai.metaheuristic.ai.yaml.meta;

import ai.metaheuristic.commons.yaml.YamlUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static ai.metaheuristic.ai.dispatcher.data.StringVariableData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Serge
 * Date: 6/27/2021
 * Time: 11:59 AM
 */
@Execution(ExecutionMode.CONCURRENT)
public class TestInlineAsVariableMeta {

    @Test
    public void test() throws IOException {
        String yamlStr = IOUtils.resourceToString("/yaml/meta/inline-as-variable-meta.yaml", StandardCharsets.UTF_8);

        Yaml yaml = YamlUtils.init(Mapping.class);

        Mapping mapping = yaml.load(yamlStr);
        assertNotNull(mapping);

        List<StringAsVar> list = mapping.mapping;

        assertNotNull(list);
        assertEquals(4, list.size());
        assertEquals(new StringAsVar("g1",null,"n1","o1"), list.get(0));
        assertEquals(new StringAsVar("g2",null,"n2","o2"), list.get(1));
        assertEquals(new StringAsVar("g3",null,"n3","o3"), list.get(2));
        assertEquals(new StringAsVar("g4",null,"n4","o4"), list.get(3));

    }
}
