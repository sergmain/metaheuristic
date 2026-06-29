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

package ai.metaheuristic.ai.internal_function.permute_variables;
import ai.metaheuristic.api.EnumsApi;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStatusService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTxService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.MhInternalTaskPipelineRunner;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.ai.spi.MhSpi;
import ch.qos.logback.classic.LoggerContext;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@SuppressWarnings("unused")
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
class TestPermuteValuesOfVariables extends PreparingSourceCode {

    @org.junit.jupiter.api.io.TempDir
    static Path tempDir;

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private TaskRepositoryForTest taskRepositoryForTest;
    @Autowired private ExecContextTxService execContextTxService;
    @Autowired private ExecContextStatusService execContextStatusService;
    @Autowired private ExecContextTaskStateService execContextTaskStateTopLevelService;
    @Autowired private ExecContextGraphTopLevelService execContextGraphTopLevelService;
    @Autowired private ExecContextRepository execContextRepository;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private MhInternalTaskPipelineRunner pipelineRunner;

    @SneakyThrows
        public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/variables/permute-values-of-variables.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @SuppressWarnings("ConstantValue")
    @AfterEach
    public void afterTestSourceCodeService() {
        System.out.println("Finished TestSourceCodeService.afterTestSourceCodeService()");
        if (getExecContextForTest() !=null) {
            ExecContextSyncService.getWithSyncNullable(getExecContextForTest().id,
                    () -> txSupportForTestingService.deleteByExecContextId(getExecContextForTest().id));
        }
    }

    /**
     * Verifies that {@code mh.permute-values-of-variables} dynamically creates one
     * subprocess task per permutation of its input variables.
     * <p>
     * The YAML sets {@code batches: '[40, 60]'} (two values) and uses
     * {@code mh.permute-values-of-variables} which reads {@code batchSize} and produces
     * one subprocess invocation per value. Expected final task count:
     * <ul>
     *   <li>mh.inline-as-variable — 1 task</li>
     *   <li>mh.permute-values-of-variables — 1 task</li>
     *   <li>mh.nop subprocess — 2 tasks (one per batchSize value)</li>
     *   <li>mh.finish — 1 task</li>
     * </ul>
     * Total: 5 finished tasks, all OK.
     * <p>
     * Previously this test stepped through {@code findTaskForRegisteringInQueue} calls
     * and asserted intermediate counts, but that path only awaits the task-allocator
     * registration step — NOT the downstream {@code @Async @EventListener} that drains
     * {@code TaskWithInternalContextEvent}s through the internal-context MTQ. The
     * intermediate assertion {@code assertEquals(2, ...)} was racing the async chain
     * and saw 0 tasks finished. The runner closes that race by directly enqueuing each
     * internal task and awaiting its terminal state. Final-count semantics are
     * preserved by {@code finalAssertions(5)}.
     */
    @Test
    public void testCreateTasks() {
        System.out.println("start produceTasksForTest()");
        preparingSourceCodeService.produceTasksForTest(resolveSourceCode(getSourceCodeAndLang()), preparingSourceCodeData);

        System.out.println("start execContextStatusService.resetStatus()");
        execContextStatusService.resetStatus();

        System.out.println("start runPipelineToCompletion()");
        pipelineRunner.runPipelineToCompletion(getExecContextForTest().id, 20);

        finalAssertions(5);
    }
}
