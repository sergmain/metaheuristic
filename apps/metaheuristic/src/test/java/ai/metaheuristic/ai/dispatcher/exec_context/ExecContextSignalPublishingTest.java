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
import ai.metaheuristic.ai.dispatcher.signal_bus.ScopeRef;
import ai.metaheuristic.ai.dispatcher.signal_bus.SignalBus;
import ai.metaheuristic.ai.dispatcher.signal_bus.SignalEntry;
import ai.metaheuristic.ai.dispatcher.signal_bus.SignalKind;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
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
 * Plan 04 — ExecContext producer wiring. Red/Green integration test.
 *
 * Uses the PreparingSourceCode harness: seeds Company / Account / SourceCode,
 * the test creates an ExecContext (via TxSupportForTestingService.createExecContext),
 * then drives ExecContextFSM.toFinished(id). After the producer is wired at the
 * FSM's state-write sites, an EXEC_CONTEXT signal with terminal=true must reach
 * the SignalBus with topic execContext.<execContextId>.state.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureCache
public class ExecContextSignalPublishingTest extends PreparingSourceCode {

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

    @Autowired ExecContextFSM execContextFSM;
    @Autowired SignalBus signalBus;
    @Autowired TxSupportForTestingService txSupport;

    @Test
    void transitionToFinished_publishesExecContextStateSignal_withTerminalTrue() {
        // arrange
        DispatcherContext ctx = new DispatcherContext(getAccount(), getCompany());
        ExecContextCreatorService.ExecContextCreationResult creation =
            txSupport.createExecContext(getSourceCode(), ctx.asUserExecContext());
        setExecContextForTest(creation.execContext);

        Long execContextId = getExecContextForTest().id;
        long companyId = getCompany().uniqueId;
        ScopeRef scope = new ScopeRef(companyId);

        // act — drive the FSM through its real Finished transition
        ExecContextSyncService.getWithSyncVoid(execContextId,
            () -> execContextFSM.toFinished(execContextId));

        // assert
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            var result = signalBus.query(scope, 0L,
                Set.of(SignalKind.EXEC_CONTEXT), List.of());
            Optional<SignalEntry> entry = result.signals().stream()
                .filter(e -> e.signalId().equals(String.valueOf(execContextId)))
                .findFirst();
            assertThat(entry).isPresent();
            SignalEntry e = entry.get();
            assertThat(e.info()).containsEntry("state",
                EnumsApi.ExecContextState.FINISHED.code);
            assertThat(e.info()).containsEntry("stateName", "FINISHED");
            assertThat(e.terminal()).isTrue();
            assertThat(e.topic()).isEqualTo("execContext." + execContextId + ".state");
        });
    }
}
