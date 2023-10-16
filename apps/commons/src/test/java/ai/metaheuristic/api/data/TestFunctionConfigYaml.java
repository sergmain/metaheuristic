/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.yaml.function.*;
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
        FunctionConfigYamlV2 sc = new FunctionConfigYamlUtilsV1().upgradeTo(getFunctionConfigYamlV1());
        FunctionConfigYaml sc2 = new FunctionConfigYamlUtilsV2().upgradeTo(sc);

        System.out.println(FunctionConfigYamlUtils.UTILS.toString(sc2));

        // to be sure that values were copied
        assertNotNull(sc.system);
        assertNotNull(sc.system.checksumMap);
        sc.system.checksumMap.put(EnumsApi.HashAlgo.SHA256WithSignature, "321qwe");
        Objects.requireNonNull(sc.function.metas).add(Map.of("key2", "value2"));

        assertEquals(sc2.function.code, "sc.code");
        assertEquals(sc2.function.type, "sc.type");
        assertEquals(sc2.function.file, "sc.file");
        assertEquals(sc2.function.params, "sc.params");
        assertEquals(sc2.function.env, "sc.env");
        assertEquals(sc2.function.sourcing, EnumsApi.FunctionSourcing.dispatcher);
        assertNotNull(sc2.function.git);
        assertEquals(sc2.function.git.repo, "repo");
        assertEquals(sc2.function.git.branch, "branch");
        assertEquals(sc2.function.git.commit, "commit");
        assertNotNull(sc2.function.metas);
        assertEquals(2, sc2.function.metas.size());
        assertNotNull(MetaUtils.getMeta(sc2.function.metas, "key1"));
        //noinspection ConstantConditions
        assertEquals("value1", MetaUtils.getMeta(sc2.function.metas, "key1").getValue());

        assertNotNull(sc2.system);
        assertNotNull(sc2.system.checksumMap);
        assertEquals(1, sc2.system.checksumMap.size());
        assertNotNull(sc2.system.checksumMap.get(EnumsApi.HashAlgo.SHA256));
    }

    @Test
    public void testUpgradeToLatest() {
        FunctionConfigYamlV1 sc1 = getFunctionConfigYamlV1();
        FunctionConfigYaml sc2 = new FunctionConfigYamlUtilsV2().upgradeTo(new FunctionConfigYamlUtilsV1().upgradeTo(sc1));
        checkLatest(sc2);
    }

    private static FunctionConfigYamlV1 getFunctionConfigYamlV1() {
        FunctionConfigYamlV1 sc = new FunctionConfigYamlV1();
        sc.code = "sc.code";
        sc.type = "sc.type";
        sc.file = "sc.file";
        sc.params = "sc.params";
        sc.env = "sc.env";
        sc.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        sc.git = new GitInfo("repo", "branch", "commit");
        assertNotNull(sc.metas);
        sc.metas.add(Map.of("key1", "value1"));
        sc.metas.add(Map.of(ConstsApi.META_MH_TASK_PARAMS_VERSION, "1"));

        assertNotNull(sc.checksumMap);
        sc.checksumMap.put(EnumsApi.HashAlgo.SHA256, "qwe321");
        return sc;
    }

    @Test
    public void test() {
        FunctionConfigYaml sc = new FunctionConfigYaml();
        sc.function.code = "sc.code";
        sc.function.type = "sc.type";
        sc.function.file = "sc.file";
        sc.function.params = "sc.params";
        sc.function.env = "sc.env";
        sc.function.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        sc.function.git = new GitInfo("repo", "branch", "commit");
        Objects.requireNonNull(sc.function.metas).add(Map.of("key1", "value1"));
        sc.function.metas.add(Map.of(ConstsApi.META_MH_TASK_PARAMS_VERSION, "1"));

        assertNotNull(sc.system);
        assertNotNull(sc.system.checksumMap);
        sc.system.checksumMap.put(EnumsApi.HashAlgo.SHA256, "qwe321");

        FunctionConfigYaml sc1 = sc.clone();

        // to be sure that values were copied, we'll change original checksumMap
        sc.system.checksumMap.put(EnumsApi.HashAlgo.SHA256WithSignature, "321qwe");
        sc.function.metas.add(Map.of("key2", "value2"));

        checkLatest(sc1);
    }

    private static void checkLatest(FunctionConfigYaml sc) {
        assertEquals(sc.function.code, "sc.code");
        assertEquals(sc.function.type, "sc.type");
        assertEquals(sc.function.file, "sc.file");
        assertEquals(sc.function.params, "sc.params");
        assertEquals(sc.function.env, "sc.env");
        assertEquals(sc.function.sourcing, EnumsApi.FunctionSourcing.dispatcher);
        assertNotNull(sc.function.git);
        assertEquals(sc.function.git.repo, "repo");
        assertEquals(sc.function.git.branch, "branch");
        assertEquals(sc.function.git.commit, "commit");
        assertNotNull(sc.function.metas);
        assertEquals(2, sc.function.metas.size());
        assertEquals("value1", Objects.requireNonNull(MetaUtils.getMeta(sc.function.metas, "key1")).getValue());

        assertNotNull(sc.system);
        assertNotNull(sc.system.checksumMap);
        assertEquals(1, sc.system.checksumMap.size());
        assertNotNull(sc.system.checksumMap.get(EnumsApi.HashAlgo.SHA256));
        assertNull(sc.system.checksumMap.get(EnumsApi.HashAlgo.SHA256WithSignature));
    }

    @Test
    public void testNull() {
        FunctionConfigYaml sc = new FunctionConfigYaml();
        sc.function.code = "sc.code";
        sc.function.type = "sc.type";
        sc.function.file = "sc.file";
        sc.function.params = "sc.params";
        sc.function.env = "sc.env";
        sc.function.sourcing = EnumsApi.FunctionSourcing.dispatcher;

        FunctionConfigYaml sc1 = sc.clone();

        assertEquals(sc1.function.code, "sc.code");
        assertEquals(sc1.function.type, "sc.type");
        assertEquals(sc1.function.file, "sc.file");
        assertEquals(sc1.function.env, "sc.env");
        assertEquals(sc1.function.sourcing, EnumsApi.FunctionSourcing.dispatcher);
        assertNull(sc1.function.git);
        assertNotNull(sc1.function.metas);
        assertTrue(sc1.function.metas.isEmpty());

        assertNotNull(sc1.system);
        assertNotNull(sc1.system.checksumMap);
        assertTrue(sc1.system.checksumMap.isEmpty());
    }
}
