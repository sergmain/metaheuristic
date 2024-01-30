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
package ai.metaheuristic.commons.yaml;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestBundleParamsYaml {

    @Test
    public void test() {

        FunctionConfigYaml cfg = new FunctionConfigYaml();

        FunctionConfigYaml.FunctionConfig config = cfg.function;
        config.code = "aiai.fit.default.function:1.0";
        config.type = CommonConsts.FIT_TYPE;
        config.file = "fit-model.py";
        config.sourcing = EnumsApi.FunctionSourcing.dispatcher;

        assertNotNull(config.metas);
        config.metas.add(Map.of(ConstsApi.META_MH_TASK_PARAMS_VERSION, "1"));

        cfg.system.checksumMap.putAll( Checksum.fromJson("{\"checksums\":{\"SHA256\":\"<some value #1>\"}}").checksums);
        cfg.system.checksumMap.putAll( Checksum.fromJson("{\"checksums\":{\"MD5\":\"<some value #2>\"}}").checksums);

        String yaml = FunctionConfigYamlUtils.UTILS.toString(cfg);
        System.out.println(yaml);

        FunctionConfigYaml fcy = FunctionConfigYamlUtils.UTILS.to(yaml);

        assertNotNull(fcy);
        assertNotNull(fcy.function);

        FunctionConfigYaml.FunctionConfig fc = fcy.function;
        assertNotNull(fc);
        assertNotNull(fcy.system);
        assertNotNull(fcy.system.getChecksumMap());
        assertNotNull(fcy.system.getChecksumMap().get(EnumsApi.HashAlgo.SHA256));
        assertNotNull(fcy.system.getChecksumMap().get(EnumsApi.HashAlgo.MD5));
        assertEquals("<some value #1>", fcy.system.getChecksumMap().get(EnumsApi.HashAlgo.SHA256));
        assertEquals("<some value #2>", fcy.system.getChecksumMap().get(EnumsApi.HashAlgo.MD5));
    }

/*
    @Test
    public void test_1() {
        FunctionConfigYamlV1.FunctionConfigV1 config = new FunctionConfigListYamlV1.FunctionConfigV1();
        config.code = "aiai.fit.default.function:1.0";
        config.type = CommonConsts.FIT_TYPE;
        config.params = "content-of-file";
        config.sourcing= EnumsApi.FunctionSourcing.processor;
        config.metas = List.of(
                Map.of(ConstsApi.META_MH_FUNCTION_PARAMS_AS_FILE_META,"true"),
                Map.of("some-meta", "111"),
                Map.of(ConstsApi.META_MH_TASK_PARAMS_VERSION, "1"));

        scs.functions.add(config);

        String yaml = FunctionConfigListYamlUtils.UTILS.toString(scs);
        System.out.println(yaml);

        FunctionConfigListYaml fcy = FunctionConfigListYamlUtils.UTILS.to(yaml);
        assertNotNull(fcy);
        assertEquals(3, fcy.version);
        assertNotNull(fcy.functions);
        assertEquals(1, fcy.functions.size());

        FunctionConfigYaml.FunctionConfig fc = fcy.functions.get(0);
        assertNotNull(fc);
        assertEquals("content-of-file", fc.content);
        assertTrue(S.b(fc.params));
        assertNull(MetaUtils.getMeta(fc.metas, ConstsApi.META_MH_FUNCTION_PARAMS_AS_FILE_META));
        assertEquals("111", MetaUtils.getValue(fc.metas, "some-meta"));

    }
*/

/*
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

        String yaml = FunctionConfigListYamlUtils.UTILS.toString(scs);
        System.out.println(yaml);
        assertThrows(CheckIntegrityFailedException.class, ()-> FunctionConfigListYamlUtils.UTILS.to(yaml));
    }
*/

    // TODO p0 2023-10-13 implement
/*    @Test
    public void test_3() throws IOException {
        String yaml = IOUtils.resourceToString("/yaml/bundle.yaml", StandardCharsets.UTF_8);

        FunctionConfigListYaml fcy = FunctionConfigListYamlUtils.UTILS.to(yaml);
        assertNotNull(fcy.functions);
        assertEquals(2, fcy.functions.size());
        {
            FunctionConfigYaml.FunctionConfig fc = fcy.functions.get(0);
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
            FunctionConfigYaml.FunctionConfig fc = fcy.functions.get(1);
            assertNotNull(fc);
            assertEquals("predict:8.0", fc.code);
            assertEquals("predict", fc.type);
            assertEquals("lstm-predict.py", fc.file);
            assertEquals(EnumsApi.FunctionSourcing.dispatcher, fc.sourcing);
            assertEquals("python-3", fc.env);
            assertTrue(S.b(fc.params));
            assertEquals("42", MetaUtils.getValue(fc.metas, "mh.task-params-version"));
        }
    }*/
}
