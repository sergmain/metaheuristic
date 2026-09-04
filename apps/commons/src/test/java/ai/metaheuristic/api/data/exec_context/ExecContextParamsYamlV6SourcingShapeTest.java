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
public class ExecContextParamsYamlV6SourcingShapeTest {

    /** yaml exactly as it was stored while Variable.git was a GitInfo and Variable.disk a DiskInfo */
    private static final String LEGACY_V6 = """
        version: 6
        clean: false
        sourceCodeUid: test-uid
        variables:
          inputs:
          - name: in-from-git
            context: local
            sourcing: git
            git:
              repo: https://github.com/sergmain/metaheuristic-assets.git
              branch: main
              commit: HEAD
              path: assets
          outputs:
          - name: out-to-disk
            context: local
            sourcing: disk
            disk:
              mask: '*.txt'
              code: some-dir-code
              path: /tmp/some-dir
        """;

    @Test
    public void test_legacyYamlStillLoadsIntoGitParams() {
        final Yaml yaml = YamlUtils.init(ExecContextParamsYamlV6.class);
        final ExecContextParamsYamlV6 v6 = yaml.load(LEGACY_V6);

        assertEquals(1, v6.variables.inputs.size());
        final ExecContextParamsYamlV6.VariableV6 in = v6.variables.inputs.get(0);
        assertNotNull(in.git, "git written under the old type must still load under the new one");
        assertEquals("https://github.com/sergmain/metaheuristic-assets.git", in.git.repo);
        assertEquals("main", in.git.branch);
        assertEquals("HEAD", in.git.commit);
        assertEquals("assets", in.git.path);
    }

    @Test
    public void test_legacyYamlStillLoadsIntoDiskParams() {
        final Yaml yaml = YamlUtils.init(ExecContextParamsYamlV6.class);
        final ExecContextParamsYamlV6 v6 = yaml.load(LEGACY_V6);

        assertEquals(1, v6.variables.outputs.size());
        final ExecContextParamsYamlV6.VariableV6 out = v6.variables.outputs.get(0);
        assertNotNull(out.disk, "disk written under the old type must still load under the new one");
        assertEquals("*.txt", out.disk.mask);
        assertEquals("some-dir-code", out.disk.code);
        assertEquals("/tmp/some-dir", out.disk.path);
    }

    @Test
    public void test_versionIsStillSix() {
        final ExecContextParamsYamlV6 v6 = YamlUtils.init(ExecContextParamsYamlV6.class).load(LEGACY_V6);
        assertEquals(6, v6.version, "the copy is a @Nullable-compatible change, it must not bump the version");
        assertEquals(6, new ExecContextParamsYaml().version);
    }

    @Test
    public void test_gitParamsRoundTripsThroughGitInfo() {
        final GitInfo src = new GitInfo("repo", "branch", "commit", "path");
        final ExecContextParamsYaml.GitParams params = ExecContextParamsYaml.GitParams.from(src);

        assertNotNull(params);
        assertEquals(src, ExecContextParamsYaml.GitParams.toGitInfo(params));
    }

    @Test
    public void test_diskParamsRoundTripsThroughDiskInfo() {
        final DiskInfo src = new DiskInfo("mask", "code", "path");
        final ExecContextParamsYaml.DiskParams params = ExecContextParamsYaml.DiskParams.from(src);

        assertNotNull(params);
        assertEquals(src, ExecContextParamsYaml.DiskParams.toDiskInfo(params));
    }

    @Test
    public void test_convertersAreNullSafe() {
        assertNull(ExecContextParamsYaml.GitParams.from(null));
        assertNull(ExecContextParamsYaml.GitParams.toGitInfo(null));
        assertNull(ExecContextParamsYaml.DiskParams.from(null));
        assertNull(ExecContextParamsYaml.DiskParams.toDiskInfo(null));
        assertNull(ExecContextParamsYamlV6.GitParamsV6.from(null));
        assertNull(ExecContextParamsYamlV6.DiskParamsV6.from(null));
    }

    @Test
    public void test_v6ConvertersCarryEveryField() {
        final ExecContextParamsYamlV6.GitParamsV6 git =
            ExecContextParamsYamlV6.GitParamsV6.from(new GitInfo("r", "b", "c", "p"));
        assertNotNull(git);
        assertEquals("r", git.repo);
        assertEquals("b", git.branch);
        assertEquals("c", git.commit);
        assertEquals("p", git.path);

        final ExecContextParamsYamlV6.DiskParamsV6 disk =
            ExecContextParamsYamlV6.DiskParamsV6.from(new DiskInfo("m", "c", "p"));
        assertNotNull(disk);
        assertEquals("m", disk.mask);
        assertEquals("c", disk.code);
        assertEquals("p", disk.path);
    }
}
