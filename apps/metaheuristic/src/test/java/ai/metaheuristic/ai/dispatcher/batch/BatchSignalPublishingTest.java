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

package ai.metaheuristic.ai.dispatcher.batch;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.Batch;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.signal_bus.ScopeRef;
import ai.metaheuristic.ai.dispatcher.signal_bus.SignalBus;
import ai.metaheuristic.ai.dispatcher.signal_bus.SignalEntry;
import ai.metaheuristic.ai.dispatcher.signal_bus.SignalKind;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Plan 03 Part B Step B1 — Red test for the Finished transition.
 * Uses the PreparingSourceCode harness to get a ready-made SourceCode + ExecContext.
 * We bind a Batch to that ExecContext, flip the ExecContext to FINISHED, and
 * invoke BatchTxService.updateBatchStatuses(). After B2 wires the producer,
 * a BatchStateSignalTxEvent must reach SignalBus.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureCache
public class BatchSignalPublishingTest extends PreparingSourceCode {

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
    @Override
    public String getSourceCodeYamlAsString() {
        return IOUtils.resourceToString(
            "/source_code/yaml/default-source-code-for-batch-testing.yaml",
            StandardCharsets.UTF_8);
    }

    @Autowired BatchTxService batchTxService;
    @Autowired SignalBus signalBus;
    @Autowired TxSupportForTestingService txSupport;

    @Test
    void transitionToFinished_publishesBatchStateSignal_withTerminalTrue() {
        // arrange — PreparingSourceCode has seeded company, account, source code.
        // We create the ExecContext explicitly (the harness does not do it by default).
        DispatcherContext ctx = new DispatcherContext(getAccount(), getCompany());
        ExecContextCreatorService.ExecContextCreationResult creation =
            txSupport.createExecContext(getSourceCode(), ctx.asUserExecContext());
        setExecContextForTest(creation.execContext);

        long companyId = getCompany().uniqueId;
        Long execContextId = getExecContextForTest().id;
        ScopeRef scope = new ScopeRef(companyId);

        // Flip the ExecContext to FINISHED so updateBatchStatuses() picks up our batch.
        ExecContextSyncService.getWithSyncVoid(execContextId,
            () -> txSupport.toFinished(execContextId));

        // Seed a Batch in Processing state bound to that ExecContext.
        long batchId = txSupport.batchCacheSave(new Batch(
            getSourceCode().id, execContextId, Enums.BatchExecState.Processing,
            getAccount().id, companyId)).id;

        // act
        batchTxService.updateBatchStatuses();

        // assert
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            var result = signalBus.query(scope, 0L, Set.of(SignalKind.BATCH), List.of());
            Optional<SignalEntry> entry = result.signals().stream()
                .filter(e -> e.signalId().equals(String.valueOf(batchId)))
                .findFirst();
            assertThat(entry).isPresent();
            SignalEntry e = entry.get();
            assertThat(e.info()).containsEntry("state", Enums.BatchExecState.Finished.code);
            assertThat(e.info()).containsEntry("stateName", "Finished");
            assertThat(e.terminal()).isTrue();
            assertThat(e.topic()).isEqualTo("batch." + batchId + ".state");
        });
    }
}
