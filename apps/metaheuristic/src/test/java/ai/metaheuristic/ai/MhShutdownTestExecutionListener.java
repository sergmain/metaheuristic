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

package ai.metaheuristic.ai;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

import java.lang.reflect.Field;

/**
 * Drains the dispatcher {@code ShutdownService} after every {@code @TempDir} Spring
 * test, in the afterTestClass (afterAll) phase — which runs BEFORE JUnit's static
 * {@code @TempDir} cleanup.
 *
 * <p>Why: Spring/JUnit does not reliably close the test's application context during
 * a run ({@code BEFORE_*} {@code @DirtiesContext} modes never close it before the
 * class's {@code @TempDir} cleanup, and a cached context lives until JVM exit). So the
 * beans' {@code @PreDestroy} — and therefore {@code ShutdownService.preDestroy()},
 * which drains the in-flight Lucene auto-index {@code IndexWriter}s — never runs in
 * time. On Windows an open {@code IndexWriter} keeps a file lock that blocks
 * {@code @TempDir} deletion ("Failed to delete temp directory"). We invoke
 * {@code preDestroy()} explicitly here; it blocks until every {@code ShutdownInterface}
 * has drained.
 *
 * <p>Gated on the presence of a {@code @TempDir} field: those tests get a unique
 * application context (unique temp-dir H2 URL via {@code @DynamicPropertySource}), so
 * the permanent shutdown is safe — the context is never reused by another class, and
 * tests without a temp dir have nothing to leak.
 *
 * <p>Runs at LOWEST_PRECEDENCE so its afterTestClass fires first (reverse order),
 * before {@code DirtiesContextTestExecutionListener} can close the context out from
 * under it.
 *
 * <p>Registered globally via {@code META-INF/spring.factories}.
 *
 * @author Sergio Lissner
 * Date: 6/3/2026
 * Time: 4:06 PM
 */
@Slf4j
public class MhShutdownTestExecutionListener implements TestExecutionListener, Ordered {

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void afterTestClass(TestContext testContext) {
/*
        if (!hasTempDirField(testContext.getTestClass())) {
            return;
        }
        try {
            ApplicationContext ctx = testContext.getApplicationContext();
            ShutdownService svc = ctx.getBeanProvider(ShutdownService.class).getIfAvailable();
            if (svc != null) {
                svc.preDestroy();
            }
        } catch (Throwable t) {
            log.warn("Best-effort shutdown drain after {} failed: {}",
                testContext.getTestClass().getSimpleName(), t.toString());
        }
*/
    }

    private static boolean hasTempDirField(Class<?> clazz) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(TempDir.class)) {
                    return true;
                }
            }
        }
        return false;
    }
}
