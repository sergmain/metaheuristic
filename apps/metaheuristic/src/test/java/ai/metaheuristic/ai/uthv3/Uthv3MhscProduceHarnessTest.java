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

package ai.metaheuristic.ai.uthv3;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UTHv3 harness support for a SourceCode defined in .mhsc DSLv2 format.
 *
 * <p>Proves the standard V3 produce path {@code step_0_0_produce_tasks_and_start()} drives a
 * {@code .mhsc} source, so a test no longer has to hand-roll the lang-aware EC-creation /
 * produceAndStartAllTasks bypass that every inline-{@code .mhsc} graft IT currently copies to
 * dodge {@code PreparingSourceCodeService.produceTasksForTest}'s YAML-only parse assertion.
 *
 * @author Sergio Lissner
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class Uthv3MhscProduceHarnessTest extends PreparingSourceCode {

    @Autowired private TaskRepositoryForTest taskRepositoryForTest;

    // A .mhsc DSLv2 source loaded from a classpath resource (the real file-based path, like RG's
    // RgTestPreparingServiceV2), NOT an inline string. Single Process, internal function mh.nop.
    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/mhsc/uthv3-single-nop-1.0.mhsc", EnumsApi.SourceCodeLang.mhsc, null);
    }

    @Test
    public void test_mhsc_drivenThroughStandardHarness() {
        // The standard V3 produce path must drive a .mhsc DSLv2 source end-to-end: produce + start
        // its tasks, leaving the ExecContext STARTED - no per-test lang-aware bypass required.
        step_0_0_produce_tasks_and_start();

        assertEquals(EnumsApi.ExecContextState.STARTED.code, getExecContextForTest().getState(),
                "the .mhsc-defined ExecContext must be STARTED after the standard harness produce path");

        List<TaskImpl> tasks = taskRepositoryForTest.findByExecContextIdAsList(getExecContextForTest().id);
        assertFalse(tasks.isEmpty(), "the single-process .mhsc source must have produced at least one task");
    }
}
