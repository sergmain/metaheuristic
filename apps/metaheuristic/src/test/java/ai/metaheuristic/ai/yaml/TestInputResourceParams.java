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

import ai.metaheuristic.api.data.InputResourceParam;
import ai.metaheuristic.ai.yaml.input_resource_param.InputResourceParamUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.*;

public class TestInputResourceParams {

    @Test
    public void test() throws IOException {

        InputResourceParam yaml;
        try(InputStream is = TestYamlParser.class.getResourceAsStream("/yaml/input_resource_params/input-resource-params.yaml")) {
            yaml = InputResourceParamUtils.to(is);
        }

/*
    poolCodes:
        aaaa:
            - bbb
            - ccc
        dddd:
            - eee
*/

        assertTrue(yaml.poolCodes.containsKey("aaaa"));
        assertTrue(yaml.poolCodes.containsKey("dddd"));

        assertTrue(yaml.poolCodes.get("aaaa").contains("bbb"));
        assertTrue(yaml.poolCodes.get("aaaa").contains("ccc"));
        assertTrue(yaml.poolCodes.get("dddd").contains("eee"));

    }
}
