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

package ai.metaheuristic.api.data;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtilsV1;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlV1;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 10/8/2019
 * Time: 9:31 PM
 */
public class TestFunctionConfigYaml {

    @Test
    public void testUpgradeToV2() {
        FunctionConfigYamlV1 sc = getFunctionConfigYamlV1();
        FunctionConfigYaml sc2 = new FunctionConfigYamlUtilsV1().upgradeTo(sc);

        System.out.println(FunctionConfigYamlUtils.BASE_YAML_UTILS.toString(sc2));

        // to be sure that values were copied
        Objects.requireNonNull(sc.checksumMap).put(EnumsApi.HashAlgo.SHA256WithSignature, "321qwe");
        Objects.requireNonNull(sc.metas).add(Map.of("key2", "value2"));

        assertEquals(sc2.code, "sc.code");
        assertEquals(sc2.type, "sc.type");
        assertEquals(sc2.file, "sc.file");
        assertEquals(sc2.params, "sc.params");
        assertEquals(sc2.env, "sc.env");
        assertEquals(sc2.sourcing, EnumsApi.FunctionSourcing.dispatcher);
        assertNotNull(sc2.checksumMap);
        assertEquals(1, sc2.checksumMap.size());
        assertNotNull(sc2.checksumMap.get(EnumsApi.HashAlgo.SHA256));
        assertNotNull(sc2.git);
        assertEquals(sc2.git.repo, "repo");
        assertEquals(sc2.git.branch, "branch");
        assertEquals(sc2.git.commit, "commit");
        assertTrue(sc2.skipParams);
        assertNotNull(sc2.metas);
        assertEquals(1, sc2.metas.size());
        assertNotNull(MetaUtils.getMeta(sc2.metas, "key1"));
        //noinspection ConstantConditions
        assertEquals("value1", MetaUtils.getMeta(sc2.metas, "key1").getValue());
    }

    @Test
    public void testUpgradeToLatest() {
        FunctionConfigYamlV1 sc1 = getFunctionConfigYamlV1();
        FunctionConfigYaml sc2 = new FunctionConfigYamlUtilsV1().upgradeTo(sc1);
        checkLatest(sc2);
//        FunctionConfigYaml sc = new FunctionConfigYamlUtilsV1().upgradeTo(sc2);
//        checkLatest(sc);
    }

    private FunctionConfigYamlV1 getFunctionConfigYamlV1() {
        FunctionConfigYamlV1 sc = new FunctionConfigYamlV1();
        sc.code = "sc.code";
        sc.type = "sc.type";
        sc.file = "sc.file";
        sc.params = "sc.params";
        sc.env = "sc.env";
        sc.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        assertNotNull(sc.checksumMap);
        sc.checksumMap.put(EnumsApi.HashAlgo.SHA256, "qwe321");
        sc.git = new GitInfo("repo", "branch", "commit");
        sc.skipParams = true;
        assertNotNull(sc.metas);
        sc.metas.add(Map.of("key1", "value1"));
        return sc;
    }

    @Test
    public void test() {
        FunctionConfigYaml sc = new FunctionConfigYaml();
        sc.code = "sc.code";
        sc.type = "sc.type";
        sc.file = "sc.file";
        sc.params = "sc.params";
        sc.env = "sc.env";
        sc.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        Objects.requireNonNull(sc.checksumMap).put(EnumsApi.HashAlgo.SHA256, "qwe321");
        sc.git = new GitInfo("repo", "branch", "commit");
        sc.skipParams = true;
        Objects.requireNonNull(sc.metas).add(Map.of("key1", "value1"));

        FunctionConfigYaml sc1 = sc.clone();

        // to be sure that values were copied, we'll change original checksumMap
        sc.checksumMap.put(EnumsApi.HashAlgo.SHA256WithSignature, "321qwe");
        sc.metas.add(Map.of("key2", "value2"));

        checkLatest(sc1);
    }

    private void checkLatest(FunctionConfigYaml sc) {
        assertEquals(sc.code, "sc.code");
        assertEquals(sc.type, "sc.type");
        assertEquals(sc.file, "sc.file");
        assertEquals(sc.params, "sc.params");
        assertEquals(sc.env, "sc.env");
        assertEquals(sc.sourcing, EnumsApi.FunctionSourcing.dispatcher);
        assertNotNull(sc.checksumMap);
        assertEquals(1, sc.checksumMap.size());
        assertNotNull(sc.checksumMap.get(EnumsApi.HashAlgo.SHA256));
        assertNull(sc.checksumMap.get(EnumsApi.HashAlgo.SHA256WithSignature));
        assertNotNull(sc.git);
        assertEquals(sc.git.repo, "repo");
        assertEquals(sc.git.branch, "branch");
        assertEquals(sc.git.commit, "commit");
        assertTrue(sc.skipParams);
        assertNotNull(sc.metas);
        assertEquals(1, sc.metas.size());
        assertEquals("value1", Objects.requireNonNull(MetaUtils.getMeta(sc.metas, "key1")).getValue());
    }

    @Test
    public void testNull() {
        FunctionConfigYaml sc = new FunctionConfigYaml();
        sc.code = "sc.code";
        sc.type = "sc.type";
        sc.file = "sc.file";
        sc.params = "sc.params";
        sc.env = "sc.env";
        sc.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        sc.skipParams = true;

        FunctionConfigYaml sc1 = sc.clone();

        assertEquals(sc1.code, "sc.code");
        assertEquals(sc1.type, "sc.type");
        assertEquals(sc1.file, "sc.file");
        assertEquals(sc1.params, "sc.params");
        assertEquals(sc1.env, "sc.env");
        assertEquals(sc1.sourcing, EnumsApi.FunctionSourcing.dispatcher);
        assertNotNull(sc1.checksumMap);
        assertTrue(sc1.checksumMap.isEmpty());
        assertNull(sc1.git);
        assertTrue(sc1.skipParams);
        assertNotNull(sc1.metas);
        assertTrue(sc1.metas.isEmpty());
    }
}
