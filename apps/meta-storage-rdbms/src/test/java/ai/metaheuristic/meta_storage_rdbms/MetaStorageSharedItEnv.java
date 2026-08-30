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

package ai.metaheuristic.meta_storage_rdbms;

import ai.metaheuristic.commons.utils.DirUtils;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * V3 harness environment - the meta-storage copy of MH's {@code SharedItEnv}.
 *
 * <p>One home dir and one H2 file DB for the WHOLE JVM run, resolved in a static
 * initialiser so every context that starts sees the same constants. That is what makes real
 * infrastructure cost one startup per run instead of one per class.
 *
 * <p>ONE store, not two: {@link #DB_URL} is the only database in play. Liquibase migrates it and the
 * meta storage lives in it, which is the whole difference between this module and the SQLite one.
 *
 * <p>Isolation between tests comes from unique names ({@link #uniqueCode}, {@link #uniqueLong}), not
 * from tearing the store down - a shared DB with per-test unique identifiers is the V3 bargain.
 *
 * @author Serge
 */
public final class MetaStorageSharedItEnv {

    public static final String MH_HOME;
    public static final String DB_URL;

    static {
        try {
            Path home = DirUtils.createMhTempPath("mh-meta-storage-");
            if (home == null) {
                throw new IllegalStateException("MH HOME DIR wasn't created");
            }
            MH_HOME = home.toAbsolutePath().toString();
            System.setProperty("mh.home", MH_HOME);
            // ONE file DB for the whole JVM run. DB_CLOSE_DELAY=-1 keeps it alive across any
            // momentary zero-connection window so it lives the entire suite.
            DB_URL = "jdbc:h2:file:" + home.resolve("db-h2/mh").toAbsolutePath()
                     + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
        }
        catch (Throwable e) {
            throw new RuntimeException("error", e);
        }
    }

    private static final AtomicInteger SEQ = new AtomicInteger();
    public static String uniqueCode(String prefix) { return prefix + "_" + SEQ.incrementAndGet(); }

    // Process-global unique Long for tests that write a UNIQUE-constrained numeric column under the
    // shared DB. High base avoids colliding with system-assigned ids.
    private static final AtomicLong LONG_SEQ = new AtomicLong(1_000_000_000L);
    public static long uniqueLong() { return LONG_SEQ.incrementAndGet(); }

    private MetaStorageSharedItEnv() {}
}
