/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.utils.asset;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Phase 2/3 of 016: AssetUtils per-OS target selection and actual-file resolution.
 */
@Execution(CONCURRENT)
public class AssetUtilsTargetTest {

    @Test
    public void test_selectTarget_exactOsArchWins() {
        TaskParamsYaml.FunctionConfig fc = new TaskParamsYaml.FunctionConfig();
        fc.targets.put(EnumsApi.OsArch.linux_amd64.key(), new TaskParamsYaml.Target("bin/linux-amd64", "call-cc"));
        fc.targets.put(CommonConsts.MH_DEFAULT_OS_KEY, new TaskParamsYaml.Target("src", "fallback"));
        TaskParamsYaml.Target t = AssetUtils.selectTarget(fc, EnumsApi.OsArch.linux_amd64);
        assertNotNull(t);
        assertEquals("call-cc", t.file);
    }

    @Test
    public void test_selectTarget_fallsBackToMhDefault() {
        TaskParamsYaml.FunctionConfig fc = new TaskParamsYaml.FunctionConfig();
        fc.targets.put(CommonConsts.MH_DEFAULT_OS_KEY, new TaskParamsYaml.Target("src", "fallback.jar"));
        TaskParamsYaml.Target t = AssetUtils.selectTarget(fc, EnumsApi.OsArch.darwin_arm64);
        assertNotNull(t);
        assertEquals("fallback.jar", t.file);
    }

    @Test
    public void test_selectTarget_noMatchNoDefault_returnsNull() {
        TaskParamsYaml.FunctionConfig fc = new TaskParamsYaml.FunctionConfig();
        fc.targets.put(EnumsApi.OsArch.linux_amd64.key(), new TaskParamsYaml.Target("bin/linux-amd64", "call-cc"));
        assertNull(AssetUtils.selectTarget(fc, EnumsApi.OsArch.windows_amd64));
    }

    @Test
    public void test_getActualFunctionFile_joinsSrcAndFile() {
        TaskParamsYaml.FunctionConfig fc = new TaskParamsYaml.FunctionConfig();
        fc.targets.put(EnumsApi.OsArch.detect().key(), new TaskParamsYaml.Target("bin/sub", "call-cc"));
        assertEquals(Path.of("bin/sub").resolve("call-cc").toString(), AssetUtils.getActualFunctionFile(fc));
    }

    @Test
    public void test_getActualFunctionFile_emptySrc_returnsFileOnly() {
        TaskParamsYaml.FunctionConfig fc = new TaskParamsYaml.FunctionConfig();
        fc.targets.put(EnumsApi.OsArch.detect().key(), new TaskParamsYaml.Target("", "call-cc"));
        assertEquals("call-cc", AssetUtils.getActualFunctionFile(fc));
    }

    @Test
    public void test_makeExecutableIfPosix_setsExecBitOnPosix() throws Exception {
        Path tmp = Files.createTempFile("mh-fn-", ".bin");
        try {
            AssetUtils.makeExecutableIfPosix(tmp);
            if (tmp.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                assertTrue(Files.isExecutable(tmp));
            }
        }
        finally {
            Files.deleteIfExists(tmp);
        }
    }
}
