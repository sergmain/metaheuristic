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

package ai.metaheuristic.ai.complex;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.MhInternalTaskPipelineRunner;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.spi.MhSpi;
import ai.metaheuristic.api.EnumsApi;
import lombok.SneakyThrows;
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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the full default-source-code-for-testing.yaml pipeline to FINISHED
 * via {@link MhInternalTaskPipelineRunner} and asserts the resulting task
 * graph: 22 tasks, all in OK state.
 *
 * <h2>Pipeline shape</h2>
 * <ol>
 *   <li>{@code mh.inline-as-variable} (internal) — explodes mh.hyper-params
 *       into seed/batchSize/timeSteps/RNN variables.</li>
 *   <li>{@code assembly-raw-file} (external, function-01:1.1) — takes
 *       global-test-variable, outputs assembled-raw-output.</li>
 *   <li>{@code dataset-processing} (external, function-02:1.1) — outputs
 *       dataset-processing-output and fans out two siblings:
 *     <ul>
 *       <li>{@code feature-processing-1} (function-03:1.1) → feature-output-1</li>
 *       <li>{@code feature-processing-2} (function-04:1.1) → feature-output-2</li>
 *     </ul>
 *   </li>
 *   <li>{@code mh.permute-values-of-variables} (internal) — permutes hyper-params,
 *       wrapping {@code mh.permute-variables} (internal) which generates six
 *       (fit-dataset, predict-dataset) pairs from the cartesian expansion of
 *       the 2-element batches hyper-param and the two feature variables.</li>
 *   <li>{@code mh.aggregate} (internal) — aggregates metrics + predicted.</li>
 *   <li>{@code mh.finish} (internal) — terminates.</li>
 * </ol>
 *
 * <h2>Why the rewrite</h2>
 * The original test drove the pipeline step-by-step through
 * {@code TaskProviderTopLevelService.findTask} on the test thread, which
 * races several {@code @Async @EventListener} chains (variable init,
 * task-state propagation, queue registration) and was flaky and slow.
 * The pipeline scaffold drains async work between iterations and handles
 * external functions via a {@link MhInternalTaskPipelineRunner.SyntheticDataProvider},
 * so the test reduces to: configure the provider, run, assert the final graph.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureCache
public class TestSourceCodeService extends PreparingSourceCode {

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
    @Autowired private ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest taskRepositoryForTestLocal;

        public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @AfterEach
    public void afterTestSourceCodeService() {
        System.out.println("Finished TestSourceCodeService.afterTestSourceCodeService()");
        if (getExecContextForTest() != null) {
            ExecContextSyncService.getWithSyncNullable(getExecContextForTest().id,
                    () -> txSupportForTestingService.deleteByExecContextId(getExecContextForTest().id));
        }
    }

    @SneakyThrows
    @Test
    public void testCreateTasks() {

        System.out.println("=== Setup: produce tasks and start exec-context ===");
        step_0_0_produce_tasks_and_start();

        // Count invocations per function. The scaffold calls the provider once per
        // task attempt; here every task is expected to succeed on the first try.
        Map<String, AtomicInteger> callCounts = new ConcurrentHashMap<>();

        MhInternalTaskPipelineRunner.SyntheticDataProvider provider = (functionCode, tpy) -> {
            callCounts.computeIfAbsent(functionCode, k -> new AtomicInteger()).incrementAndGet();
            return switch (functionCode) {
                case "function-01:1.1" ->
                        // assembly-raw-file: outputs assembled-raw-output
                        Map.of("assembled-raw-output", "assembled-raw-output-result");
                case "function-02:1.1" ->
                        // dataset-processing: outputs dataset-processing-output
                        Map.of("dataset-processing-output", "dataset-processing-output-result");
                case "function-03:1.1" ->
                        // feature-processing-1: outputs feature-output-1
                        Map.of("feature-output-1", "feature-output-1-result");
                case "function-04:1.1" ->
                        // feature-processing-2: outputs feature-output-2
                        Map.of("feature-output-2", "feature-output-2-result");
                case "test.fit.function:1.0" ->
                        // fit-dataset: 6 inputs → 1 output (model). One model per permutation
                        // context — distinguish via taskContextId so each permutation gets
                        // a unique payload.
                        Map.of("model", "model-" + tpy.task.taskContextId);
                case "test.predict.function:1.0" ->
                        // predict-dataset: 3 inputs → 2 outputs (metrics, predicted)
                        Map.of(
                                "metrics", "metrics-" + tpy.task.taskContextId,
                                "predicted", "predicted-" + tpy.task.taskContextId);
                default -> throw new IllegalStateException(
                        "Unexpected external function call: '" + functionCode + "'");
            };
        };

        System.out.println("=== Run pipeline to completion ===");
        pipelineRunner.runPipelineToCompletion(getExecContextForTest().id, provider, 80);

        System.out.println("=== Assertions ===");

        // Each external function must have been called the expected number of times.
        // The permutation expands feature-output-1/2 into 3 contexts, so fit/predict
        // each run 3 times. Other externals run exactly once.
        assertEquals(1, callOf(callCounts, "function-01:1.1"), "assembly-raw-file");
        assertEquals(1, callOf(callCounts, "function-02:1.1"), "dataset-processing");
        assertEquals(1, callOf(callCounts, "function-03:1.1"), "feature-processing-1");
        assertEquals(1, callOf(callCounts, "function-04:1.1"), "feature-processing-2");
        // mh.permute-variables expands over the 2-element batches array AND the
        // (feature-output-1, feature-output-2) pair, producing 6 (fit, predict)
        // pairs at runtime.
        assertEquals(6, callOf(callCounts, "test.fit.function:1.0"), "fit-dataset (6 permutations)");
        assertEquals(6, callOf(callCounts, "test.predict.function:1.0"), "predict-dataset (6 permutations)");

        // Full task graph: 22 tasks, all OK. The scaffold ensures FINISHED state
        // before returning, but verifyGraphIntegrity + per-task assertions
        // confirm the DB and graph agree and nothing slipped to ERROR / SKIPPED.
        ExecContextSyncService.getWithSyncVoid(getExecContextForTest().id, () -> {
            verifyGraphIntegrity();

            List<TaskImpl> tasks = taskRepositoryForTestLocal.findByExecContextIdAsList(getExecContextForTest().id);
            assertEquals(22, tasks.size(), "Expected 22 tasks total");

            for (TaskImpl task : tasks) {
                assertNotNull(task);
                assertTrue(task.completed != 0,
                        "Task #" + task.id + " (" + task.getTaskParamsYaml().task.processCode + ") not completed");
                assertEquals(EnumsApi.TaskExecState.OK.value, task.execState,
                        "Task #" + task.id + " (" + task.getTaskParamsYaml().task.processCode + ") not OK, state="
                                + EnumsApi.TaskExecState.from(task.execState));
            }
        });
    }

    private static int callOf(Map<String, AtomicInteger> counts, String functionCode) {
        AtomicInteger n = counts.get(functionCode);
        return n == null ? 0 : n.get();
    }
}
