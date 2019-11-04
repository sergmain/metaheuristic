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
import ai.metaheuristic.commons.yaml.snippet.*;
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
public class TestSnippetConfigYaml {

    @Test
    public void testUpgradeToV2() {
        SnippetConfigYamlV1 sc = getSnippetConfigYamlV1();
        SnippetConfigYamlV2 sc2 = new SnippetConfigYamlUtilsV1().upgradeTo(sc);

        // to be sure that values were copied
        sc.checksumMap.put(EnumsApi.Type.SHA256WithSignature, "321qwe");
        sc.metas.add(new Meta("key2", "value2", "ext2" ));

        assertEquals(sc2.code, "sc.code");
        assertEquals(sc2.type, "sc.type");
        assertEquals(sc2.file, "sc.file");
        assertEquals(sc2.params, "sc.params");
        assertEquals(sc2.env, "sc.env");
        assertEquals(sc2.sourcing, EnumsApi.SnippetSourcing.launchpad);
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
        SnippetConfigYamlV1 sc1 = getSnippetConfigYamlV1();
        SnippetConfigYamlV2 sc2 = new SnippetConfigYamlUtilsV1().upgradeTo(sc1);
        SnippetConfigYaml sc = new SnippetConfigYamlUtilsV2().upgradeTo(sc2);
        checkLatest(sc);
    }

    private SnippetConfigYamlV1 getSnippetConfigYamlV1() {
        SnippetConfigYamlV1 sc = new SnippetConfigYamlV1();
        sc.code = "sc.code";
        sc.type = "sc.type";
        sc.file = "sc.file";
        sc.params = "sc.params";
        sc.env = "sc.env";
        sc.sourcing = EnumsApi.SnippetSourcing.launchpad;
        sc.metrics = true;
        sc.checksumMap = new HashMap<>(Map.of(EnumsApi.Type.SHA256, "qwe321"));
        sc.info = new SnippetConfigYamlV1.SnippetInfoV1(true, 42);
        sc.checksum = "sc.checksum";
        sc.git = new GitInfo("repo", "branch", "commit");
        sc.skipParams = true;
        sc.metas = new ArrayList<>(List.of( new Meta("key1", "value1", "ext1" )));
        return sc;
    }

    @Test
    public void test() {
        SnippetConfigYaml sc = new SnippetConfigYaml();
        sc.code = "sc.code";
        sc.type = "sc.type";
        sc.file = "sc.file";
        sc.params = "sc.params";
        sc.env = "sc.env";
        sc.sourcing = EnumsApi.SnippetSourcing.launchpad;
        sc.ml = new SnippetConfigYaml.MachineLearning(true, false);
        sc.checksumMap = new HashMap<>(Map.of(EnumsApi.Type.SHA256, "qwe321"));
        sc.info = new SnippetConfigYaml.SnippetInfo(true, 42);
        sc.checksum = "sc.checksum";
        sc.git = new GitInfo("repo", "branch", "commit");
        sc.skipParams = true;
        sc.metas = new ArrayList<>(List.of( new Meta("key1", "value1", "ext1" )));

        SnippetConfigYaml sc1 = sc.clone();

        // to be sure that values were copied
        sc.checksumMap.put(EnumsApi.Type.SHA256WithSignature, "321qwe");
        sc.metas.add(new Meta("key2", "value2", "ext2" ));

        checkLatest(sc1);
    }

    private void checkLatest(SnippetConfigYaml sc) {
        assertEquals(sc.code, "sc.code");
        assertEquals(sc.type, "sc.type");
        assertEquals(sc.file, "sc.file");
        assertEquals(sc.params, "sc.params");
        assertEquals(sc.env, "sc.env");
        assertEquals(sc.sourcing, EnumsApi.SnippetSourcing.launchpad);
        assertNotNull(sc.ml);
        assertTrue(sc.ml.metrics);
        assertEquals(1, sc.checksumMap.size());
        assertNotNull(sc.checksumMap.get(EnumsApi.Type.SHA256));
        assertEquals(sc.info.length, 42);
        assertTrue(sc.info.signed);
        assertEquals(sc.checksum, "sc.checksum");
        assertEquals(sc.git.repo, "repo");
        assertEquals(sc.git.branch, "branch");
        assertEquals(sc.git.commit, "commit");
        assertTrue(sc.skipParams);
        assertEquals(1, sc.metas.size());
        assertEquals("key1", sc.metas.get(0).getKey());
        assertEquals("value1", sc.metas.get(0).getValue());
    }

    @Test
    public void testNull() {
        SnippetConfigYaml sc = new SnippetConfigYaml();
        sc.code = "sc.code";
        sc.type = "sc.type";
        sc.file = "sc.file";
        sc.params = "sc.params";
        sc.env = "sc.env";
        sc.sourcing = EnumsApi.SnippetSourcing.launchpad;
        sc.ml = new SnippetConfigYaml.MachineLearning(true, false);
        sc.checksumMap = null;
        sc.info = null;
        sc.checksum = "sc.checksum";
        sc.git = null;
        sc.skipParams = true;
        sc.metas = null;

        SnippetConfigYaml sc1 = sc.clone();

        assertEquals(sc1.code, "sc.code");
        assertEquals(sc1.type, "sc.type");
        assertEquals(sc1.file, "sc.file");
        assertEquals(sc1.params, "sc.params");
        assertEquals(sc1.env, "sc.env");
        assertEquals(sc1.sourcing, EnumsApi.SnippetSourcing.launchpad);
        assertNotNull(sc1.ml);
        assertTrue(sc1.ml.metrics);
        assertNull(sc1.checksumMap);
        assertNull(sc1.info);
        assertEquals(sc1.checksum, "sc.checksum");
        assertNull(sc1.git);
        assertTrue(sc1.skipParams);
        assertNull(sc1.metas);
    }
}
