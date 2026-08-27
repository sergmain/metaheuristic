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

package ai.metaheuristic.ai.dispatcher.license;

import ai.metaheuristic.ai.Globals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * The caching contract of {@link LicenseInstallationService#installationId}.
 *
 * <p>Spring-less: the identity is whatever the supplier answers, so nothing here needs a database.
 *
 * <p>❗ Both tests below need a COLD cache, and the cache is a JVM-wide static, so only whichever
 * of them runs first in a given JVM sees one. Run them one at a time until that changes.
 *
 * @author Serge
 */
@Execution(CONCURRENT)
public class LicenseInstallationServiceTest {

    /** A service over a fresh temp {@code mh.home} whose TX service answers with a fixed id. */
    private static LicenseInstallationService serviceOn(String id) throws IOException {
        final Path home = Files.createTempDirectory("mh-home-");
        final Globals globals = new Globals();
        globals.home = home;
        globals.dispatcherPath = Files.createDirectories(home.resolve("dispatcher"));

        return new LicenseInstallationService(globals, new LicenseInstallationTxService(null) {
            @Override
            public String getOrCreateInstallationId() {
                return id;
            }
        });
    }

    @Test
    public void test_twoInstances_mintIndependently() throws IOException {
        // one dispatcher owns one identity, and the cache exists to keep the verify path off the
        // database - it is not a place for one instance to answer on behalf of another.
        final LicenseInstallationService alpha = serviceOn("id-alpha");
        final LicenseInstallationService beta = serviceOn("id-beta");

        assertEquals("id-alpha", alpha.installationId(), "alpha must answer with its own id");
        assertEquals("id-beta", beta.installationId(), "beta must answer with its own id");
    }

    /**
     * The race, rather than the leak the test above shows sequentially.
     *
     * <p>Four installations call at the same moment. Exactly one of them reaches the synchronized
     * block first; its supplier is the only one ever consulted, its consumer is the only mirror
     * ever written, and the other three are handed an identity they did not mint and never wrote
     * to disk. WHICH one wins is decided by thread scheduling and differs between runs, so the
     * observed value in the failure below is not stable - only the collapse to a single value is.
     *
     * <p>What that costs in production: three dispatchers report a licence-bound identity that
     * belongs to a fourth, and their {@code mh.home} holds no {@code installation-id.txt} at all,
     * because {@code consumer.accept(id)} is inside the block none of them entered.
     */
    @Test
    public void test_concurrentInstances_raceForTheSharedCache() throws Exception {
        final int n = 4;
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            final List<Future<String>> answers = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                final LicenseInstallationService service = serviceOn("id-" + i);
                answers.add(pool.submit(() -> {
                    start.await();
                    return service.installationId();
                }));
            }

            // released together, so the winner is whichever thread the scheduler lets in first
            start.countDown();

            final Set<String> observed = new TreeSet<>();
            for (Future<String> answer : answers) {
                observed.add(answer.get(30, TimeUnit.SECONDS));
            }

            assertEquals(Set.of("id-0", "id-1", "id-2", "id-3"), observed,
                    "each of the " + n + " installations must answer with the id its own supplier minted");
        }
        finally {
            pool.shutdownNow();
        }
    }
}
