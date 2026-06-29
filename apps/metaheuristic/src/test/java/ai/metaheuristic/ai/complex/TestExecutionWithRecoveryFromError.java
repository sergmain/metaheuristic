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

package ai.metaheuristic.ai.complex;
import ai.metaheuristic.api.EnumsApi;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.MhInternalTaskPipelineRunner;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.spi.MhSpi;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression guard for the ERROR_WITH_RECOVERY flow.
 *
 * <h2>Scenario</h2>
 * The YAML declares three real processes plus the implicit mh.finish:
 * <ol>
 *   <li>{@code mh.inline-as-variable} (internal) — explodes the inline hyper-params
 *       into individual output variables (seed/batchSize/timeSteps/RNN).</li>
 *   <li>{@code assembly-raw-file} (external, function-01:1.1) — declares
 *       {@code triesAfterError: 1}, so the dispatcher should tolerate one failure
 *       before giving up. The test simulates a failure on the first attempt and a
 *       success on the retry, asserting that the recovery path actually fires.</li>
 *   <li>{@code dataset-processing} (external, function-02:1.1) — runs after
 *       assembly-raw-file succeeds.</li>
 * </ol>
 * Expected final state: 4 tasks (the three above + mh.finish), all OK.
 *
 * <h2>Why this rewrite</h2>
 * The original test drove execution through {@code TaskProviderTopLevelService.findTask}
 * on the test thread, which races an {@code @Async @EventListener} chain (publishes
 * {@code InputVariablesInitedEvent} on task assignment) and surfaces as a flaky
 * {@code ObjectOptimisticLockingFailureException} during {@code storeExecResultWithTx}.
 * The new flow uses {@link MhInternalTaskPipelineRunner} with a stateful
 * {@code SyntheticDataProvider}: it returns {@code null} on the first call to
 * function-01:1.1 (signaling failure) and returns the expected outputs on every
 * subsequent call. The runner handles the ERROR_WITH_RECOVERY → reset → retry
 * loop internally, so the test asserts only the contract (final state: 4 tasks
 * OK, and the failing function was called twice).
 *
 * @author Sergio Lissner
 * Date: 5/21/2026
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class TestExecutionWithRecoveryFromError extends PreparingSourceCode {

    @org.junit.jupiter.api.io.TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbUrl = "jdbc:h2:file:" + tempDir.resolve("db-h2/mh").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE";
        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("mh.home", () -> tempDir.toAbsolutePath().toString());
        registry.add("spring.profiles.active", () -> "dispatcher,h2,test");
    }

    @BeforeAll
    static void setSystemProperties() {
        MhSpi.cleanUpOnShutdown();
        System.setProperty("mh.home", tempDir.toAbsolutePath().toString());
    }

    @AfterAll
    static void cleanupLogging() {
        MhSpi.cleanUpOnShutdown();
    }

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private MhInternalTaskPipelineRunner pipelineRunner;

    @Override
    @SneakyThrows
        public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/source-code-for-testing-error-recovery.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @AfterEach
    public void afterTest() {
        System.out.println("Finished TestExecutionWithRecoveryFromError.afterTest()");
        ExecContextSyncService.getWithSyncNullable(getExecContextForTest().id,
            () -> txSupportForTestingService.deleteByExecContextId(getExecContextForTest().id));
    }

    @SneakyThrows
    @Test
    public void test() {
        System.out.println("=== Setup: produce tasks ===");
        step_0_0_produce_tasks_and_start();

        // Counts how many times each external function was invoked. The runner re-calls
        // the provider for each task attempt, so function-01:1.1 should be called twice
        // (once failure, once success) and function-02:1.1 once (success only).
        AtomicInteger fn01Calls = new AtomicInteger();
        AtomicInteger fn02Calls = new AtomicInteger();

        MhInternalTaskPipelineRunner.SyntheticDataProvider provider = (functionCode, tpy) -> {
            switch (functionCode) {
                case "function-01:1.1" -> {
                    int attempt = fn01Calls.incrementAndGet();
                    if (attempt == 1) {
                        // First attempt: simulate failure. Source-code declares
                        // triesAfterError=1, so the dispatcher transitions the task
                        // to ERROR_WITH_RECOVERY and the runner auto-fires the
                        // recovery reset; the next loop iteration calls this provider
                        // again with attempt==2 and we return success.
                        return null;
                    }
                    return Map.of("assembled-raw-output", "assembled-raw-output-result");
                }
                case "function-02:1.1" -> {
                    fn02Calls.incrementAndGet();
                    return Map.of("dataset-processing-output", "dataset-processing-output-result");
                }
                default -> throw new IllegalStateException(
                        "Unexpected external function call: '" + functionCode + "'");
            }
        };

        System.out.println("=== Run pipeline to completion ===");
        pipelineRunner.runPipelineToCompletion(getExecContextForTest().id, provider, 30);

        // function-01:1.1 must have been called twice — once for the simulated failure,
        // once for the post-recovery success. If recovery didn't fire, attempt==1 would
        // be the final state and the runner would never re-enter for this task.
        assertEquals(2, fn01Calls.get(),
                "function-01:1.1 should have been called twice (failure + recovery success); " +
                        "if it was called once, ERROR_WITH_RECOVERY recovery did not fire");
        assertEquals(1, fn02Calls.get(),
                "function-02:1.1 should have been called exactly once");

        // Final invariant: 4 tasks total (inline-as-variable, assembly-raw-file,
        // dataset-processing, mh.finish), all in OK state, ExecContext FINISHED.
        finalAssertions(4);
    }
}
