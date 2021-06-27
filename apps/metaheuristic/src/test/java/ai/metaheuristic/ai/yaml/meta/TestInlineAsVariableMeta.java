/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.data.InlineVariableData;
import ai.metaheuristic.commons.yaml.YamlUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ai.metaheuristic.ai.dispatcher.data.InlineVariableData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Serge
 * Date: 6/27/2021
 * Time: 11:59 AM
 */
public class TestInlineAsVariableMeta {

    @Test
    public void test() throws IOException {
        String yamlStr = IOUtils.resourceToString("/yaml/meta/inline-as-variable-meta.yaml", StandardCharsets.UTF_8);

        Yaml yaml = YamlUtils.init(Mapping.class);

        Mapping mapping = yaml.load(yamlStr);
        assertNotNull(mapping);

        List<InlineAsVar> list = mapping.mapping;

        assertNotNull(list);
        assertEquals(4, list.size());
        assertEquals(new InlineAsVar("g1","n1","o1"), list.get(0));
        assertEquals(new InlineAsVar("g2","n2","o2"), list.get(1));
        assertEquals(new InlineAsVar("g3","n3","o3"), list.get(2));
        assertEquals(new InlineAsVar("g4","n4","o4"), list.get(3));

    }
}
