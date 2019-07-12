/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

import ai.metaheuristic.ai.yaml.workbook.WorkbookParamsYamlUtils;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;

public class TestInputResourceParams {

    @Test
    public void test() throws IOException {

        String str = IOUtils.resourceToString("/yaml/input_resource_params/input-resource-params.yaml", StandardCharsets.UTF_8);
        WorkbookParamsYaml yaml = WorkbookParamsYamlUtils.BASE_YAML_UTILS.to(str);

/*
    poolCodes:
        aaaa:
            - bbb
            - ccc
        dddd:
            - eee
*/

        assertTrue(yaml.workbookYaml.poolCodes.containsKey("aaaa"));
        assertTrue(yaml.workbookYaml.poolCodes.containsKey("dddd"));

        assertTrue(yaml.workbookYaml.poolCodes.get("aaaa").contains("bbb"));
        assertTrue(yaml.workbookYaml.poolCodes.get("aaaa").contains("ccc"));
        assertTrue(yaml.workbookYaml.poolCodes.get("dddd").contains("eee"));

    }
}
