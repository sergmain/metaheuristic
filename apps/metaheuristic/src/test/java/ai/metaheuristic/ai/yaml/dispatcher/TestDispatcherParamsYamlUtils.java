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

package ai.metaheuristic.ai.yaml.dispatcher;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Serge
 * Date: 6/21/2021
 * Time: 4:50 PM
 */
public class TestDispatcherParamsYamlUtils {

    @Test
    public void test() throws IOException {
        String yaml = IOUtils.resourceToString("/yaml/dispatcher/dispatcher-with-long-running.yaml", StandardCharsets.UTF_8);
        DispatcherParamsYaml p = DispatcherParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        assertNotNull(p);

        assertNotNull(p.batches);
        assertNotNull(p.experiments);
        assertNotNull(p.longRunnings);

        assertEquals(3, p.batches.size());
        assertEquals(5, p.experiments.size());
        assertEquals(1, p.longRunnings.size());
    }
}
