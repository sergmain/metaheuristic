/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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
package ai.metaheuristic.commons.yaml;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.yaml.bundle.BundleParamsYaml;
import ai.metaheuristic.commons.yaml.bundle.BundleParamsYamlUtils;
import ai.metaheuristic.commons.yaml.bundle.FunctionConfigListYamlV1;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestBundleParamsYaml {

    @Test
    public void test() {
        BundleParamsYaml scs = new BundleParamsYaml();
        scs.functions = new ArrayList<>();

        BundleParamsYaml.FunctionConfig config = new BundleParamsYaml.FunctionConfig();
        config.code = "aiai.fit.default.function:1.0";
        config.type = CommonConsts.FIT_TYPE;
        config.file = "fit-model.py";
        config.metas.add(
                Map.of(ConstsApi.META_MH_TASK_PARAMS_VERSION, "1"));

        config.checksumMap = Checksum.fromJson("{\"checksums\":{\"SHA256\":\"<some value #1>\"}}").checksums;
        config.checksumMap.putAll( Checksum.fromJson("{\"checksums\":{\"MD5\":\"<some value #2>\"}}").checksums);

        scs.functions.add(config);

        String yaml = BundleParamsYamlUtils.BASE_YAML_UTILS.toString(scs);
        System.out.println(yaml);

        BundleParamsYaml fcy = BundleParamsYamlUtils.BASE_YAML_UTILS.to(yaml);

        assertNotNull(fcy);
        assertNotNull(fcy.functions);
        assertEquals(1, fcy.functions.size());

        BundleParamsYaml.FunctionConfig fc = fcy.functions.get(0);
        assertNotNull(fc);
        assertNotNull(fc.getChecksumMap());
        assertNotNull(fc.getChecksumMap().get(EnumsApi.HashAlgo.SHA256));
        assertNotNull(fc.getChecksumMap().get(EnumsApi.HashAlgo.MD5));
        assertEquals("<some value #1>", fc.getChecksumMap().get(EnumsApi.HashAlgo.SHA256));
        assertEquals("<some value #2>", fc.getChecksumMap().get(EnumsApi.HashAlgo.MD5));
    }

    @Test
    public void test_1() {
        FunctionConfigListYamlV1 scs = new FunctionConfigListYamlV1();
        scs.functions = new ArrayList<>();

        FunctionConfigListYamlV1.FunctionConfigV1 config = new FunctionConfigListYamlV1.FunctionConfigV1();
        config.code = "aiai.fit.default.function:1.0";
        config.type = CommonConsts.FIT_TYPE;
        config.params = "content-of-file";
        config.sourcing= EnumsApi.FunctionSourcing.processor;
        config.metas = List.of(
                Map.of(ConstsApi.META_MH_FUNCTION_PARAMS_AS_FILE_META,"true"),
                Map.of("some-meta", "111"),
                Map.of(ConstsApi.META_MH_TASK_PARAMS_VERSION, "1"));

        scs.functions.add(config);

        String yaml = BundleParamsYamlUtils.BASE_YAML_UTILS.toString(scs);
        System.out.println(yaml);

        BundleParamsYaml fcy = BundleParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        assertNotNull(fcy);
        assertEquals(3, fcy.version);
        assertNotNull(fcy.functions);
        assertEquals(1, fcy.functions.size());

        BundleParamsYaml.FunctionConfig fc = fcy.functions.get(0);
        assertNotNull(fc);
        assertEquals("content-of-file", fc.content);
        assertTrue(S.b(fc.params));
        assertNull(MetaUtils.getMeta(fc.metas, ConstsApi.META_MH_FUNCTION_PARAMS_AS_FILE_META));
        assertEquals("111", MetaUtils.getValue(fc.metas, "some-meta"));

    }

    @Test
    public void test_2() {
        FunctionConfigListYamlV1 scs = new FunctionConfigListYamlV1();
        scs.functions = new ArrayList<>();

        FunctionConfigListYamlV1.FunctionConfigV1 config = new FunctionConfigListYamlV1.FunctionConfigV1();
        config.code = "aiai.fit.default.function:1.0";
        config.type = CommonConsts.FIT_TYPE;
        config.params = "content-of-file";
        config.sourcing= EnumsApi.FunctionSourcing.processor;
        config.metas.add(
                Map.of("aaa","true",
                        "some-meta", "111",
                        ConstsApi.META_MH_TASK_PARAMS_VERSION, "1"));

        scs.functions.add(config);

        String yaml = FunctionConfigListYamlUtils.BASE_YAML_UTILS.toString(scs);
        System.out.println(yaml);
        assertThrows(CheckIntegrityFailedException.class, ()->FunctionConfigListYamlUtils.BASE_YAML_UTILS.to(yaml));
    }

    @Test
    public void test_3() throws IOException {
        String yaml = IOUtils.resourceToString("/yaml/bundle.yaml", StandardCharsets.UTF_8);

        BundleParamsYaml fcy = BundleParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        assertNotNull(fcy.functions);
        assertEquals(2, fcy.functions.size());
        {
            BundleParamsYaml.FunctionConfig fc = fcy.functions.get(0);
            assertNotNull(fc);
            assertEquals("fit:8.0", fc.code);
            assertEquals("fit", fc.type);
            assertEquals("lstm-fit.py", fc.file);
            assertEquals(EnumsApi.FunctionSourcing.processor, fc.sourcing);
            assertEquals("python-3", fc.env);
            assertTrue(S.b(fc.params));
            assertEquals("41", MetaUtils.getValue(fc.metas, "mh.task-params-version"));
        }
        {
            BundleParamsYaml.FunctionConfig fc = fcy.functions.get(1);
            assertNotNull(fc);
            assertEquals("predict:8.0", fc.code);
            assertEquals("predict", fc.type);
            assertEquals("lstm-predict.py", fc.file);
            assertEquals(EnumsApi.FunctionSourcing.dispatcher, fc.sourcing);
            assertEquals("python-3", fc.env);
            assertTrue(S.b(fc.params));
            assertEquals("42", MetaUtils.getValue(fc.metas, "mh.task-params-version"));
        }
    }
}