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
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.beans.Batch;
import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.data.BatchData;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Plan 03 Part A Step A1 — characterize the current legacy-endpoint behavior
 * BEFORE touching any production code. Seeds five batches (four states for
 * companyA + one for companyB), calls getBatchExecStatuses(ctxCompanyA),
 * asserts exact legacy shape with scope-exclusion.
 *
 * This test must remain GREEN throughout Plan 03 — it is the invariant
 * against which the producer instrumentation is judged non-regressing.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class BatchExecStatusesCharacterizationTest {

    // unique companyIds per test execution — JVM-unique AtomicLong avoids collision
    // with any other seeded batches in the shared h2 db
    private static final AtomicLong COMPANY_ID_GEN = new AtomicLong(10_000L);

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

    @Autowired BatchTopLevelService batchTopLevelService;
    @Autowired TxSupportForTestingService txSupport;

    @Test
    void getBatchExecStatuses_returnsAllBatchesOfCompanyWithExactState() {
        // arrange
        long companyA = COMPANY_ID_GEN.incrementAndGet();
        long companyB = COMPANY_ID_GEN.incrementAndGet();

        long b1 = seedBatch(companyA, Enums.BatchExecState.Stored);
        long b2 = seedBatch(companyA, Enums.BatchExecState.Preparing);
        long b3 = seedBatch(companyA, Enums.BatchExecState.Processing);
        long b4 = seedBatch(companyA, Enums.BatchExecState.Finished);
        long bOther = seedBatch(companyB, Enums.BatchExecState.Finished);

        DispatcherContext ctx = contextFor(companyA);

        // act
        BatchData.ExecStatuses result = batchTopLevelService.getBatchExecStatuses(ctx);

        // assert — exact legacy shape for companyA only
        assertThat(result.statuses)
            .extracting(s -> tuple(s.id, s.state))
            .contains(
                tuple(b1, Enums.BatchExecState.Stored.code),
                tuple(b2, Enums.BatchExecState.Preparing.code),
                tuple(b3, Enums.BatchExecState.Processing.code),
                tuple(b4, Enums.BatchExecState.Finished.code))
            .doesNotContain(
                tuple(bOther, Enums.BatchExecState.Finished.code));
    }

    // -- helpers --

    private long seedBatch(long companyId, Enums.BatchExecState state) {
        // Non-null sourceCodeId/execContextId/accountId — the legacy query only
        // filters on companyId, so dummy ids are acceptable for this characterization.
        Batch b = new Batch(1L, 1L, state, 1L, companyId);
        b = txSupport.batchCacheSave(b);
        return b.id;
    }

    private DispatcherContext contextFor(long companyId) {
        Account a = new Account();
        a.id = companyId * 100 + 1;
        a.username = "char-test-user-" + companyId;
        Company c = new Company();
        c.uniqueId = companyId;
        return new DispatcherContext(a, c);
    }
}
