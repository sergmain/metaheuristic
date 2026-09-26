/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.api.data.exec_context;

import ai.metaheuristic.api.sourcing.DiskInfo;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.json.versioning_json.BaseJsonUtils;
import ai.metaheuristic.commons.yaml.YamlUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Variable.git / Variable.disk changed type from the shared GitInfo / DiskInfo to this params class's
 * own GitParams / DiskParams, WITHOUT a version bump. That is only safe because the field names are
 * identical, so yaml written before the change still loads. These tests are what makes that claim
 * checkable rather than asserted.
 *
 * @author Sergio Lissner
 * Date: 9/3/2026
 * Time: 6:20 PM
 */
@Execution(ExecutionMode.CONCURRENT)
public class ExecContextParamsV1SourcingShapeTest {

    /** yaml exactly as it was stored while Variable.git was a GitInfo and Variable.disk a DiskInfo */
    private static final String STORED_V1 = """
        {
          "version": 1,
          "clean": false,
          "sourceCodeUid": "test-uid",
          "variables": {
            "inputs": [
              {
                "name": "in-from-git",
                "context": "local",
                "sourcing": "git",
                "git": {
                  "repo": "https://github.com/sergmain/metaheuristic-assets.git",
                  "branch": "main",
                  "commit": "HEAD",
                  "path": "assets"
                }
              }
            ],
            "outputs": [
              {
                "name": "out-to-disk",
                "context": "local",
                "sourcing": "disk",
                "disk": {
                  "mask": "*.txt",
                  "code": "some-dir-code",
                  "path": "/tmp/some-dir"
                }
              }
            ]
          }
        }
        """;

    private static ExecContextParamsV1 loadStoredV1() {
        return BaseJsonUtils.getMapper().readValue(STORED_V1, ExecContextParamsV1.class);
    }

    @Test
    public void test_storedJsonLoadsIntoGitParams() {
        final ExecContextParamsV1 v1 = loadStoredV1();

        assertEquals(1, v1.variables.inputs.size());
        final ExecContextParamsV1.VariableV1 in = v1.variables.inputs.get(0);
        assertEquals(ai.metaheuristic.api.EnumsApi.DataSourcing.git, in.getSourcing());
        assertNotNull(in.git, "git must load into GitParamsV1");
        assertEquals("https://github.com/sergmain/metaheuristic-assets.git", in.git.repo);
        assertEquals("main", in.git.branch);
        assertEquals("HEAD", in.git.commit);
        assertEquals("assets", in.git.path);
    }

    @Test
    public void test_storedJsonLoadsIntoDiskParams() {
        final ExecContextParamsV1 v1 = loadStoredV1();

        assertEquals(1, v1.variables.outputs.size());
        final ExecContextParamsV1.VariableV1 out = v1.variables.outputs.get(0);
        assertEquals(ai.metaheuristic.api.EnumsApi.DataSourcing.disk, out.getSourcing());
        assertNotNull(out.disk, "disk must load into DiskParamsV1");
        assertEquals("*.txt", out.disk.mask);
        assertEquals("some-dir-code", out.disk.code);
        assertEquals("/tmp/some-dir", out.disk.path);
    }

    @Test
    public void test_versionIsOne() {
        final ExecContextParamsV1 v1 = loadStoredV1();
        assertEquals(1, v1.version, "V1 is the head of the JSON chain");
        assertEquals(1, new ExecContextParams().version);
    }

    @Test
    public void test_gitParamsRoundTripsThroughGitInfo() {
        final GitInfo src = new GitInfo("repo", "branch", "commit", "path");
        final ExecContextParams.GitParams params = ExecContextParams.GitParams.from(src);

        assertNotNull(params);
        assertEquals(src, ExecContextParams.GitParams.toGitInfo(params));
    }

    @Test
    public void test_diskParamsRoundTripsThroughDiskInfo() {
        final DiskInfo src = new DiskInfo("mask", "code", "path");
        final ExecContextParams.DiskParams params = ExecContextParams.DiskParams.from(src);

        assertNotNull(params);
        assertEquals(src, ExecContextParams.DiskParams.toDiskInfo(params));
    }

    @Test
    public void test_convertersAreNullSafe() {
        assertNull(ExecContextParams.GitParams.from(null));
        assertNull(ExecContextParams.GitParams.toGitInfo(null));
        assertNull(ExecContextParams.DiskParams.from(null));
        assertNull(ExecContextParams.DiskParams.toDiskInfo(null));
        assertNull(ExecContextParamsV1.GitParamsV1.from(null));
        assertNull(ExecContextParamsV1.DiskParamsV1.from(null));
    }

    @Test
    public void test_v1ConvertersCarryEveryField() {
        final ExecContextParamsV1.GitParamsV1 git =
            ExecContextParamsV1.GitParamsV1.from(new GitInfo("r", "b", "c", "p"));
        assertNotNull(git);
        assertEquals("r", git.repo);
        assertEquals("b", git.branch);
        assertEquals("c", git.commit);
        assertEquals("p", git.path);

        final ExecContextParamsV1.DiskParamsV1 disk =
            ExecContextParamsV1.DiskParamsV1.from(new DiskInfo("m", "c", "p"));
        assertNotNull(disk);
        assertEquals("m", disk.mask);
        assertEquals("c", disk.code);
        assertEquals("p", disk.path);
    }
}
