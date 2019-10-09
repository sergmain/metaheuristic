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
public class TestSnippetConfigClone {

    @Test
    public void test() {
        SnippetApiData.SnippetConfig sc = new SnippetApiData.SnippetConfig();
        sc.code = "sc.code";
        sc.type = "sc.type";
        sc.file = "sc.file";
        sc.params = "sc.params";
        sc.env = "sc.env";
        sc.sourcing = EnumsApi.SnippetSourcing.launchpad;
        sc.metrics = true;
        sc.checksumMap = new HashMap<>(Map.of(EnumsApi.Type.SHA256, "qwe321"));
        sc.info = new SnippetApiData.SnippetConfig.SnippetInfo(true, 42);
        sc.checksum = "sc.checksum";
        sc.git = new GitInfo("repo", "branch", "commit");
        sc.skipParams = true;
        sc.metas = new ArrayList<>(List.of( new Meta("key1", "value1", "ext1" )));

        SnippetApiData.SnippetConfig sc1 = sc.clone();

        sc.checksumMap.put(EnumsApi.Type.SHA256WithSignature, "321qwe");
        sc.metas.add(new Meta("key2", "value2", "ext2" ));

        assertEquals(sc1.code, "sc.code");
        assertEquals(sc1.type, "sc.type");
        assertEquals(sc1.file, "sc.file");
        assertEquals(sc1.params, "sc.params");
        assertEquals(sc1.env, "sc.env");
        assertEquals(sc1.sourcing, EnumsApi.SnippetSourcing.launchpad);
        assertTrue(sc1.metrics);
        assertEquals(1, sc1.checksumMap.size());
        assertNotNull(sc1.checksumMap.get(EnumsApi.Type.SHA256));
        assertEquals(sc1.info.length, 42);
        assertTrue(sc1.info.signed);
        assertEquals(sc1.checksum, "sc.checksum");
        assertEquals(sc1.git.repo, "repo");
        assertEquals(sc1.git.branch, "branch");
        assertEquals(sc1.git.commit, "commit");
        assertTrue(sc1.skipParams);
        assertEquals(1, sc1.metas.size());
        assertEquals("key1", sc1.metas.get(0).getKey());
        assertEquals("value1", sc1.metas.get(0).getValue());
    }

    @Test
    public void testNull() {
        SnippetApiData.SnippetConfig sc = new SnippetApiData.SnippetConfig();
        sc.code = "sc.code";
        sc.type = "sc.type";
        sc.file = "sc.file";
        sc.params = "sc.params";
        sc.env = "sc.env";
        sc.sourcing = EnumsApi.SnippetSourcing.launchpad;
        sc.metrics = true;
        sc.checksumMap = null;
        sc.info = null;
        sc.checksum = "sc.checksum";
        sc.git = null;
        sc.skipParams = true;
        sc.metas = null;

        SnippetApiData.SnippetConfig sc1 = sc.clone();

        assertEquals(sc1.code, "sc.code");
        assertEquals(sc1.type, "sc.type");
        assertEquals(sc1.file, "sc.file");
        assertEquals(sc1.params, "sc.params");
        assertEquals(sc1.env, "sc.env");
        assertEquals(sc1.sourcing, EnumsApi.SnippetSourcing.launchpad);
        assertTrue(sc1.metrics);
        assertNull(sc1.checksumMap);
        assertNull(sc1.info);
        assertEquals(sc1.checksum, "sc.checksum");
        assertNull(sc1.git);
        assertTrue(sc1.skipParams);
        assertNull(sc1.metas);
    }
}
