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
package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextVariableState;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextVariableStateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.utils.JsonUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Measures the REAL wall-clock cost of cloning an ExecContext's Variable rows
 * (the parallel {@code insertNewVariable} fan-out inside
 * {@link ExecContextCloneService#cloneExecContext}). Built to settle the
 * assume-vs-measure question about open_stage latency: is the clone slow, or
 * does it block on something that never returns?
 *
 * <p>Each test seeds a fresh ExecContext (NO tasks, so the task-clone passes are
 * empty and only the variable clone is timed) with a controlled number of
 * synthetic Variable rows and a matching variable-state JSON, then clones and
 * logs {@link ExecContextCloneService.CloneResult#elapsedMillis()} plus an
 * independent wall-clock measurement.
 *
 * @author Sergio Lissner
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
@Slf4j
public class ExecContextCloneVariableTimingTest extends PreparingSourceCode {

    @org.junit.jupiter.api.io.TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbUrl = "jdbc:h2:file:" + tempDir.resolve("db-h2/mh").toAbsolutePath()
                + ";DB_CLOSE_ON_EXIT=FALSE";
        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("mh.home", () -> tempDir.toAbsolutePath().toString());
        registry.add("spring.profiles.active", () -> "dispatcher,h2,test");
    }

    @SneakyThrows
        public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/default-source-code-for-batch-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @Autowired ExecContextCloneService cloneService;
    @Autowired ExecContextRepository execContextRepository;
    @Autowired ExecContextVariableStateRepository ecvsRepo;
    @Autowired VariableRepository variableRepository;
    @Autowired TxSupportForTestingService txSupport;
    @Autowired PlatformTransactionManager txManager;

    @Test
    void test_cloneVariables_10() {
        runCloneTiming(10);
    }

    @Test
    void test_cloneVariables_100() {
        runCloneTiming(100);
    }

    private void runCloneTiming(int variableCount) {
        // arrange — fresh ExecContext (no tasks: isolates the variable-clone cost)
        DispatcherContext ctx = new DispatcherContext(getAccount(), getCompany());
        var creation = txSupport.createExecContext(getSourceCode(), ctx.asUserExecContext());
        setExecContextForTest(creation.execContext);
        Long sourceId = getExecContextForTest().id;

        // seed N synthetic Variable rows + a variable-state JSON that lists them,
        // committed BEFORE the clone (cloneExecContext asserts checkTxNotExists()).
        seedSyntheticVariables(sourceId, variableCount);

        // act — clone, guarded by a 5s preemptive timeout so a blocking hang FAILS
        // the test instead of stalling forever. CloneResult.elapsedMillis is the
        // service's own measurement.
        long wallStart = System.currentTimeMillis();
        ExecContextCloneService.CloneResult result = assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> cloneService.cloneExecContext(sourceId),
                () -> "cloneExecContext(" + sourceId + ") with " + variableCount
                        + " variables did not finish within 5s — a blocking hang, not slow I/O");
        long wallElapsed = System.currentTimeMillis() - wallStart;

        // assert — exactly N variables were discovered and cloned
        assertThat(result).isNotNull();
        assertThat(result.variableCount()).isEqualTo(variableCount);

        ExecContextImpl clone = execContextRepository.findByIdNullable(result.clonedExecContextId());
        assertThat(clone).isNotNull();
        assertThat(clone.state).isEqualTo(EnumsApi.ExecContextState.FINISHED.code);

        log.warn("CLONE-TIMING variables={} serviceElapsedMs={} wallElapsedMs={}",
                variableCount, result.elapsedMillis(), wallElapsed);
    }

    private void seedSyntheticVariables(Long sourceEcId, int n) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            ExecContextImpl ec = execContextRepository.findById(sourceEcId).orElseThrow();

            ExecContextApiData.VariableState state = new ExecContextApiData.VariableState();
            state.taskId = 1L;
            state.execContextId = sourceEcId;
            state.taskContextId = "1";
            state.process = "synthetic-process";
            state.functionCode = "synthetic-fn";
            List<ExecContextApiData.VariableInfo> outs = new ArrayList<>(n);

            for (int i = 0; i < n; i++) {
                Variable v = new Variable();
                v.inited = true;
                v.nullified = false;
                v.variableBlobId = null;
                v.name = "synthetic-var-" + i;
                v.execContextId = sourceEcId;
                v.taskContextId = "1";
                v.uploadTs = new Timestamp(System.currentTimeMillis());
                v.setParams("");
                Variable saved = variableRepository.save(v);
                outs.add(new ExecContextApiData.VariableInfo(
                        saved.id, v.name, EnumsApi.VariableContext.local, ".txt"));
            }
            state.outputs = outs;

            ExecContextApiData.ExecContextVariableStates states =
                    new ExecContextApiData.ExecContextVariableStates();
            states.states.add(state);

            final String json;
            try {
                json = JsonUtils.getMapper().writeValueAsString(states);
            } catch (Exception e) {
                throw new RuntimeException("failed to serialize synthetic variable-state JSON", e);
            }

            ExecContextVariableState vs = ecvsRepo.findById(ec.execContextVariableStateId).orElseThrow();
            vs.setParams(json);
            ecvsRepo.save(vs);
        });
    }
}
