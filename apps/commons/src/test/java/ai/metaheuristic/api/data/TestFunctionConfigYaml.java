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

package ai.metaheuristic.api.data;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.yaml.function.*;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Serge
 * Date: 10/8/2019
 * Time: 9:31 PM
 */
public class TestFunctionConfigYaml {

    @Test
    public void testUpgradeToV2() {
        FunctionConfigYamlV1 sc = getFunctionConfigYamlV1();
        FunctionConfigYamlV2 sc2 = new FunctionConfigYamlUtilsV1().upgradeTo(sc);

        // to be sure that values were copied
        sc.checksumMap.put(EnumsApi.Type.SHA256WithSignature, "321qwe");
        sc.metas.add(new Meta("key2", "value2", "ext2" ));

        assertEquals(sc2.code, "sc.code");
        assertEquals(sc2.type, "sc.type");
        assertEquals(sc2.file, "sc.file");
        assertEquals(sc2.params, "sc.params");
        assertEquals(sc2.env, "sc.env");
        assertEquals(sc2.sourcing, EnumsApi.FunctionSourcing.dispatcher);
        assertNotNull(sc2.ml);
        assertTrue(sc2.ml.metrics);
        assertEquals(1, sc2.checksumMap.size());
        assertNotNull(sc2.checksumMap.get(EnumsApi.Type.SHA256));
        assertEquals(sc2.info.length, 42);
        assertTrue(sc2.info.signed);
        assertEquals(sc2.checksum, "sc.checksum");
        assertEquals(sc2.git.repo, "repo");
        assertEquals(sc2.git.branch, "branch");
        assertEquals(sc2.git.commit, "commit");
        assertTrue(sc2.skipParams);
        assertEquals(1, sc2.metas.size());
        assertEquals("key1", sc2.metas.get(0).getKey());
        assertEquals("value1", sc2.metas.get(0).getValue());
    }

    @Test
    public void testUpgradeToLatest() {
        FunctionConfigYamlV1 sc1 = getFunctionConfigYamlV1();
        FunctionConfigYamlV2 sc2 = new FunctionConfigYamlUtilsV1().upgradeTo(sc1);
        FunctionConfigYaml sc = new FunctionConfigYamlUtilsV2().upgradeTo(sc2);
        checkLatest(sc);
    }

    private FunctionConfigYamlV1 getFunctionConfigYamlV1() {
        FunctionConfigYamlV1 sc = new FunctionConfigYamlV1();
        sc.code = "sc.code";
        sc.type = "sc.type";
        sc.file = "sc.file";
        sc.params = "sc.params";
        sc.env = "sc.env";
        sc.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        sc.metrics = true;
        sc.checksumMap = new HashMap<>(Map.of(EnumsApi.Type.SHA256, "qwe321"));
        sc.info = new FunctionConfigYamlV1.FunctionInfoV1(true, 42);
        sc.checksum = "sc.checksum";
        sc.git = new GitInfo("repo", "branch", "commit");
        sc.skipParams = true;
        sc.metas = new ArrayList<>(List.of( new Meta("key1", "value1", "ext1" )));
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
        sc.ml = new FunctionConfigYaml.MachineLearning(true, false);
        sc.checksumMap.put(EnumsApi.Type.SHA256, "qwe321");
        sc.info.signed= true;
        sc.info.length = 42;
        sc.checksum = "sc.checksum";
        sc.git = new GitInfo("repo", "branch", "commit");
        sc.skipParams = true;
        sc.metas.add((new Meta("key1", "value1", "ext1" )));

        FunctionConfigYaml sc1 = sc.clone();

        // to be sure that values were copied
        sc.checksumMap.put(EnumsApi.Type.SHA256WithSignature, "321qwe");
        sc.metas.add(new Meta("key2", "value2", "ext2" ));

        checkLatest(sc1);
    }

    private void checkLatest(FunctionConfigYaml sc) {
        assertEquals(sc.code, "sc.code");
        assertEquals(sc.type, "sc.type");
        assertEquals(sc.file, "sc.file");
        assertEquals(sc.params, "sc.params");
        assertEquals(sc.env, "sc.env");
        assertEquals(sc.sourcing, EnumsApi.FunctionSourcing.dispatcher);
        assertNotNull(sc.ml);
        assertTrue(sc.ml.metrics);
        assertEquals(2, sc.checksumMap.size());
        assertNotNull(sc.checksumMap.get(EnumsApi.Type.SHA256));
        assertNotNull(sc.checksumMap.get(EnumsApi.Type.SHA256WithSignature));
        assertEquals(sc.info.length, 42);
        assertTrue(sc.info.signed);
        assertEquals(sc.checksum, "sc.checksum");
        assertNotNull(sc.git);
        assertEquals(sc.git.repo, "repo");
        assertEquals(sc.git.branch, "branch");
        assertEquals(sc.git.commit, "commit");
        assertTrue(sc.skipParams);
        assertEquals(3, sc.metas.size());
        assertEquals("key1", sc.metas.get(0).getKey());
        assertEquals("value1", sc.metas.get(0).getValue());
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
        sc.ml = new FunctionConfigYaml.MachineLearning(true, false);
        sc.checksum = "sc.checksum";
        sc.skipParams = true;

        FunctionConfigYaml sc1 = sc.clone();

        assertEquals(sc1.code, "sc.code");
        assertEquals(sc1.type, "sc.type");
        assertEquals(sc1.file, "sc.file");
        assertEquals(sc1.params, "sc.params");
        assertEquals(sc1.env, "sc.env");
        assertEquals(sc1.sourcing, EnumsApi.FunctionSourcing.dispatcher);
        assertNotNull(sc1.ml);
        assertTrue(sc1.ml.metrics);
        assertTrue(sc1.checksumMap.isEmpty());
        assertEquals(sc1.checksum, "sc.checksum");
        assertNull(sc1.git);
        assertTrue(sc1.skipParams);
        assertTrue(sc1.metas.isEmpty());
    }
}
