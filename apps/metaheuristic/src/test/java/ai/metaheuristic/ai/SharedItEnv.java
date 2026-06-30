package ai.metaheuristic.ai;

import ai.metaheuristic.commons.utils.DirUtils;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Sergio Lissner
 * Date: 6/24/2026
 * Time: 8:56 PM
 */
public final class SharedItEnv {

    public static final String MH_HOME;
    public static final String DB_URL;
    public static final String WEB_DB_URL;

    static {
        try {
            Path home = DirUtils.createMhTempPath("mh-");
            if (home==null) {
                throw new IllegalStateException("MH HOME DIR wasn't created");
            }
            MH_HOME = home.toAbsolutePath().toString();
            System.setProperty("mh.home", MH_HOME);   // <-- resolves ${mh.home} at env-prepared time
            // ONE file DB for the whole JVM run. DB_CLOSE_DELAY=-1 keeps it alive
            // across any momentary zero-connection window so it lives the entire suite.
            DB_URL = "jdbc:h2:file:" + home.resolve("db-h2/mh").toAbsolutePath()
                     + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
            // Isolated DB for forked web/security/MockMvc contexts (see MhWebItTest): they can't share
            // the single pipeline context, so an own H2 keeps their dispatcher schedulers from mutating
            // the shared pipeline DB's tasks.
            WEB_DB_URL = "jdbc:h2:file:" + home.resolve("db-h2-web/mh").toAbsolutePath()
                     + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
        } catch (Throwable e) {
            throw new RuntimeException("error", e);
        }
    }

    private static final AtomicInteger SEQ = new AtomicInteger();
    public static String uniqueCode(String prefix) { return prefix + "_" + SEQ.incrementAndGet(); }

    // Process-global unique Long for tests that write a UNIQUE-constrained numeric column under the
    // shared DB (e.g. RG_REQUIREMENT.DOCUMENT_ID / UK_RG_REQ_DOCUMENT). High base avoids colliding
    // with system-assigned ids.
    private static final AtomicLong LONG_SEQ = new AtomicLong(1_000_000_000L);
    public static long uniqueLong() { return LONG_SEQ.incrementAndGet(); }

    private SharedItEnv() {}
}