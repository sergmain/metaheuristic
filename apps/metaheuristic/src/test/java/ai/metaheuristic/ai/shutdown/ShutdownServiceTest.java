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

package ai.metaheuristic.ai.shutdown;

import ai.metaheuristic.ai.dispatcher.license.LicenseInfoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.GenericApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ShutdownService} entry points, Spring-less.
 *
 * <p>No doubles. The contexts are real (never refreshed) {@link GenericApplicationContext}s - the
 * service only compares the event's context with its own by identity. The {@link ShutdownInterface}
 * implementations are real {@link LicenseInfoService}s built with null collaborators: their
 * {@code shutdown()} only flips their own flag and reaches no collaborator, so the assertions read
 * real production state. Every {@code @Test} builds its own instances, so the class runs concurrently.
 *
 * @author Sergio Lissner
 */
@Execution(ExecutionMode.CONCURRENT)
public class ShutdownServiceTest {

    private static LicenseInfoService newImpl() {
        return new LicenseInfoService(null, null, null, null, null);
    }

    @Test
    public void test_ownContextClosed_shutsDownEveryImplementation_andClearsList() {
        final GenericApplicationContext ctx = new GenericApplicationContext();
        final LicenseInfoService a = newImpl();
        final LicenseInfoService b = newImpl();
        final ShutdownService service = new ShutdownService(new ArrayList<>(List.of(a, b)), ctx);

        service.onContextClosed(new ContextClosedEvent(ctx));

        assertTrue(a.isShutdown(), "first implementation must be shut down on its own context's close");
        assertTrue(b.isShutdown(), "second implementation must be shut down on its own context's close");
        assertTrue(service.shutdowns.isEmpty());
    }

    @Test
    public void test_foreignContextClosed_leavesImplementationsRunning() {
        final GenericApplicationContext own = new GenericApplicationContext();
        final GenericApplicationContext child = new GenericApplicationContext(own);
        final LicenseInfoService a = newImpl();
        final ShutdownService service = new ShutdownService(new ArrayList<>(List.of(a)), own);

        service.onContextClosed(new ContextClosedEvent(child));

        assertFalse(a.isShutdown(), "a child context's close event must not shut down the parent's beans");
        assertEquals(1, service.shutdowns.size());

        // the service's own close still works after ignoring the foreign one
        service.onContextClosed(new ContextClosedEvent(own));
        assertTrue(a.isShutdown());
    }

    @Test
    public void test_preDestroyAfterContextClosed_runsNothingSecondTime() {
        final GenericApplicationContext ctx = new GenericApplicationContext();
        final LicenseInfoService first = newImpl();
        final ShutdownService service = new ShutdownService(new ArrayList<>(List.of(first)), ctx);

        service.onContextClosed(new ContextClosedEvent(ctx));
        assertTrue(first.isShutdown());

        // whatever is in the list by the time @PreDestroy fires, the second entry point must not run it
        final LicenseInfoService late = newImpl();
        service.shutdowns.add(late);
        service.preDestroy();

        assertFalse(late.isShutdown(), "@PreDestroy after ContextClosedEvent must be a no-op");
    }

    @Test
    public void test_preDestroyWithoutEvent_shutsDownEveryImplementation() {
        final GenericApplicationContext ctx = new GenericApplicationContext();
        final LicenseInfoService a = newImpl();
        final ShutdownService service = new ShutdownService(new ArrayList<>(List.of(a)), ctx);

        service.preDestroy();

        assertTrue(a.isShutdown(), "@PreDestroy is the fallback when no ContextClosedEvent was seen");
        assertTrue(service.shutdowns.isEmpty());
    }

    @Test
    public void test_contextClosedAfterPreDestroy_runsNothingSecondTime() {
        final GenericApplicationContext ctx = new GenericApplicationContext();
        final LicenseInfoService first = newImpl();
        final ShutdownService service = new ShutdownService(new ArrayList<>(List.of(first)), ctx);

        service.preDestroy();
        assertTrue(first.isShutdown());

        final LicenseInfoService late = newImpl();
        service.shutdowns.add(late);
        service.onContextClosed(new ContextClosedEvent(ctx));

        assertFalse(late.isShutdown(), "ContextClosedEvent after @PreDestroy must be a no-op");
    }
}
