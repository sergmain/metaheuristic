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
package aiai.ai.metaheuristic.commons.yaml;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.yaml.function_list.FunctionConfigListYaml;
import ai.metaheuristic.commons.yaml.function_list.FunctionConfigListYamlUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestFunctionConfig {

    @Test
    public void test() {
        FunctionConfigListYaml scs = new FunctionConfigListYaml();
        scs.functions = new ArrayList<>();

        FunctionConfigListYaml.FunctionConfig config = new FunctionConfigListYaml.FunctionConfig();
        config.code = "aiai.fit.default.function:1.0";
        config.type = CommonConsts.FIT_TYPE;
        config.file = "fit-model.py";

        config.checksumMap = Checksum.fromJson("{\"checksums\":{\"SHA256\":\"<some value #1>\"}}").checksums;
        config.checksumMap.putAll( Checksum.fromJson("{\"checksums\":{\"MD5\":\"<some value #2>\"}}").checksums);

        scs.functions.add(config);

        String yaml = FunctionConfigListYamlUtils.BASE_YAML_UTILS.toString(scs);
        System.out.println(yaml);

        FunctionConfigListYaml fcy = FunctionConfigListYamlUtils.BASE_YAML_UTILS.to(yaml);

        assertNotNull(fcy);
        assertNotNull(fcy.functions);
        assertEquals(1, fcy.functions.size());

        FunctionConfigListYaml.FunctionConfig fc = fcy.functions.get(0);
        assertNotNull(fc);
        assertNotNull(fc.getChecksumMap());
        assertNotNull(fc.getChecksumMap().get(EnumsApi.Type.SHA256));
        assertNotNull(fc.getChecksumMap().get(EnumsApi.Type.MD5));
        assertEquals("<some value #1>", fc.getChecksumMap().get(EnumsApi.Type.SHA256));
        assertEquals("<some value #2>", fc.getChecksumMap().get(EnumsApi.Type.MD5));
    }

}
