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

package ai.metaheuristic.ai.dispatcher.source_code;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.SharedItEnv;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Latch writes on one MH_SOURCE_CODE row from parallel callers.
 *
 * <p>RG's implicit execution of a project increases the RG latch on the SourceCode of the project's pipeline, and every
 * project of one pipeline shares that row. Projects started in parallel therefore write the latch of one row at the
 * same moment - in production two of them 8 ms apart, and the second write failed with an optimistic lock failure on
 * {@code update mh_source_code ... where id=? and version=?}.
 *
 * <p>Each {@code @Test} writes its own latch code from {@link SharedItEnv#uniqueCode(String)}, so the counts it asserts
 * are its own even though the SourceCode row is shared by every {@code @Test} of the class.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class SourceCodeLatchConcurrencyTest extends PreparingSourceCode {

    private static final int THREADS = 16;
    private static final int CALLS_PER_THREAD = 25;

    private static final String MHSC_SOURCE = """
            source "test-source-code-latch-concurrency-1.0" {
                step := internal mh.nop {
                }
            }
            """;

    @Autowired private SourceCodeLatchService sourceCodeLatchService;
    @Autowired private SourceCodeLatchTxService sourceCodeLatchTxService;
    @Autowired private SourceCodeRepository sourceCodeRepository;
    @Autowired private SourceCodeCache sourceCodeCache;
    @Autowired private PlatformTransactionManager transactionManager;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://test-source-code-latch-concurrency", EnumsApi.SourceCodeLang.mhsc, MHSC_SOURCE);
    }

    @Test
    public void test_parallelIncreaseLatch_onOneSourceCode() throws InterruptedException, ExecutionException {
        final String code = SharedItEnv.uniqueCode("LATCH");
        final Long sourceCodeId = getSourceCode().id;
        final SourceCodeImpl before = Objects.requireNonNull(sourceCodeRepository.findByIdNullable(sourceCodeId));

        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        final List<Future<List<String>>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < THREADS; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    final List<String> failures = new ArrayList<>();
                    for (int i = 0; i < CALLS_PER_THREAD; i++) {
                        try {
                            sourceCodeLatchService.increaseLatch(sourceCodeId, code);
                        }
                        catch (Throwable th) {
                            failures.add(th.getClass().getName());
                        }
                    }
                    return failures;
                }));
            }
            start.countDown();
            await().atMost(Duration.ofMinutes(5)).pollInterval(Duration.ofMillis(200))
                    .until(() -> futures.stream().allMatch(Future::isDone));
        }
        finally {
            pool.shutdownNow();
        }

        final List<String> failures = new ArrayList<>();
        for (Future<List<String>> f : futures) {
            failures.addAll(f.get());
        }
        final int total = THREADS * CALLS_PER_THREAD;
        final SourceCodeImpl after = Objects.requireNonNull(sourceCodeRepository.findByIdNullable(sourceCodeId));
        final int count = SourceCodeUtils.parseLatch(after.latch).getOrDefault(code, 0);

        // parallel latch writers on one SourceCode are serialized - none loses a race on the row's @Version
        assertEquals(List.of(), failures, "no parallel increaseLatch call on one SourceCode fails");
        assertEquals(total, count, "every increaseLatch call landed in the latch");

        assertEquals(before.version + count, after.version.intValue(), "each landed latch write bumps the row's version once");
        final SourceCodeImpl cached = Objects.requireNonNull(sourceCodeCache.findById(sourceCodeId));
        assertEquals(after.latch, cached.latch, "a SourceCode read through the L2 cache sees the latch the row holds");
    }

    @Test
    public void test_staleFullRowSave_afterLatchWrite_failsInsteadOfOverwritingTheLatch() {
        final String code = SharedItEnv.uniqueCode("LATCH");
        final Long sourceCodeId = getSourceCode().id;
        final SourceCodeImpl stale = Objects.requireNonNull(sourceCodeRepository.findByIdNullable(sourceCodeId));

        sourceCodeLatchService.increaseLatch(sourceCodeId, code);

        final TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThrows(OptimisticLockingFailureException.class, () -> tx.executeWithoutResult(s -> sourceCodeCache.save(stale)),
                "a full-row save of a SourceCode read before a latch write must fail on its @Version, not overwrite the latch");

        final SourceCodeImpl after = Objects.requireNonNull(sourceCodeRepository.findByIdNullable(sourceCodeId));
        assertEquals(1, SourceCodeUtils.parseLatch(after.latch).getOrDefault(code, 0), "the latch write survives the stale save");
    }

    @Test
    public void test_increaseDecreaseReset_onOneSourceCode() {
        final String code = SharedItEnv.uniqueCode("LATCH");
        final String other = SharedItEnv.uniqueCode("LATCH");
        final Long sourceCodeId = getSourceCode().id;

        sourceCodeLatchService.increaseLatch(sourceCodeId, code);
        sourceCodeLatchService.increaseLatch(sourceCodeId, code);
        sourceCodeLatchService.increaseLatch(sourceCodeId, other);
        assertEquals(2, latchCount(sourceCodeId, code), "two increases");
        assertEquals(1, latchCount(sourceCodeId, other), "one increase");

        sourceCodeLatchService.decreaseLatch(sourceCodeId, code);
        assertEquals(1, latchCount(sourceCodeId, code), "one decrease");

        sourceCodeLatchService.decreaseLatch(sourceCodeId, code);
        assertFalse(SourceCodeUtils.present(code, latchOf(sourceCodeId)), "a count of 0 is not serialized");

        sourceCodeLatchService.decreaseLatch(sourceCodeId, code);
        assertFalse(SourceCodeUtils.present(code, latchOf(sourceCodeId)), "a decrease below 0 changes nothing");
        assertEquals(1, latchCount(sourceCodeId, other), "decreasing one code leaves the other one alone");

        sourceCodeLatchService.resetLatch(sourceCodeId, other);
        assertFalse(SourceCodeUtils.present(other, latchOf(sourceCodeId)), "reset removes the code");
    }

    @Test
    public void test_latchTxCall_withoutSourceCodeLock_isRefused() {
        final String code = SharedItEnv.uniqueCode("LATCH");
        final Long sourceCodeId = getSourceCode().id;

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> sourceCodeLatchTxService.increaseLatch(sourceCodeId, code),
                "a latch write that does not hold the SourceCode's write lock is the unserialized race, so it is refused");
        assertTrue(e.getMessage().startsWith("969.020"), e.getMessage());
        assertFalse(SourceCodeUtils.present(code, latchOf(sourceCodeId)), "the refused call wrote nothing");

        SourceCodeSyncService.getWithSyncVoid(sourceCodeId, () -> sourceCodeLatchTxService.increaseLatch(sourceCodeId, code));
        assertEquals(1, latchCount(sourceCodeId, code), "the same call under the lock writes the latch");
    }

    private int latchCount(Long sourceCodeId, String code) {
        return SourceCodeUtils.parseLatch(latchOf(sourceCodeId)).getOrDefault(code, 0);
    }

    private String latchOf(Long sourceCodeId) {
        return Objects.requireNonNull(sourceCodeRepository.findByIdNullable(sourceCodeId)).latch;
    }
}
