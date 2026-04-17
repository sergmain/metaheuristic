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

package ai.metaheuristic.ai.dispatcher.signal_bus;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.signal_bus.events.BatchStateSignalTxEvent;
import ai.metaheuristic.ai.dispatcher.signal_bus.events.DocumentExportSignalEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Plan 02 — Step 6 + Step 7. Verifies the Tx path end to end (AFTER_COMMIT
 * bridge fires, listener writes to bus) and the rollback case (rolled-back
 * tx never reaches the bus). Step 7 covers the plain (non-Tx) path too.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class SignalBusTxIntegrationTest {

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

    @Autowired ApplicationEventPublisher publisher;
    @Autowired SignalBus signalBus;
    @Autowired PlatformTransactionManager txManager;

    @Test
    void txEvent_visibleInBusAfterCommit() {
        ScopeRef scope = new ScopeRef(100L);

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            publisher.publishEvent(new BatchStateSignalTxEvent(42L, 4, scope,
                Map.of("state", 4, "stateName", "Finished", "companyId", 100L)));
            // pre-commit: AFTER_COMMIT not fired yet
            var mid = signalBus.query(scope, 0L, Set.of(SignalKind.BATCH), List.of());
            assertThat(mid.signals()).isEmpty();
        });

        // after commit — bridge fires, listener runs, bus has it
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            var after = signalBus.query(scope, 0L, Set.of(SignalKind.BATCH), List.of());
            assertThat(after.signals()).hasSize(1);
            assertThat(after.signals().get(0).signalId()).isEqualTo("42");
            assertThat(after.signals().get(0).terminal()).isTrue();
        });
    }

    @Test
    void txEvent_rolledBackTx_neverReachesBus() throws InterruptedException {
        ScopeRef scope = new ScopeRef(200L);

        assertThatThrownBy(() ->
            new TransactionTemplate(txManager).executeWithoutResult(status -> {
                publisher.publishEvent(new BatchStateSignalTxEvent(99L, 4, scope,
                    Map.of("state", 4)));
                throw new RuntimeException("forced rollback");
            })
        ).hasMessageContaining("forced rollback");

        // Give any misconfigured async listener time to fire, then assert nothing arrived.
        // If the bridge is ever changed to plain @EventListener, this test goes red.
        Thread.sleep(500);
        var result = signalBus.query(scope, 0L, Set.of(SignalKind.BATCH), List.of());
        assertThat(result.signals()).isEmpty();
    }

    @Test
    void plainEvent_visibleInBusImmediately() {
        ScopeRef scope = new ScopeRef(300L);

        publisher.publishEvent(new DocumentExportSignalEvent(
            "export:42:abc", scope,
            Map.of("phase", "rendering", "projectId", 42L,
                   "completed", 10, "total", 100, "percent", 10.0)));

        // No transaction involved. Listener is synchronous. Available now.
        var result = signalBus.query(scope, 0L, Set.of(SignalKind.DOCUMENT_EXPORT), List.of());
        assertThat(result.signals()).hasSize(1);
        assertThat(result.signals().get(0).info()).containsEntry("percent", 10.0);
        assertThat(result.signals().get(0).terminal()).isFalse();
    }
}
