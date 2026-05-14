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

package ai.metaheuristic.ai.dispatcher.vault;

import ai.metaheuristic.ai.Globals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Stage 5 — verify that {@link VaultService#putApiKey(long, String, String)} and
 * {@link VaultService#deleteApiKey(long, String)} publish {@link VaultEntryChangedEvent}
 * via {@code ApplicationEventPublisher} on success, and do NOT publish on failure
 * paths (vault locked, entry missing).
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
class VaultServiceEventPublishTest {

    /** Captures every event the unit under test publishes. */
    private static final class CapturingPublisher implements ApplicationEventPublisher {
        final List<Object> events = new ArrayList<>();
        @Override public void publishEvent(Object event) { events.add(event); }
        @Override public void publishEvent(ApplicationEvent event) { events.add(event); }
    }

    private static VaultService newService(Path mhHome, ApplicationEventPublisher pub) throws Exception {
        Globals globals = new Globals();
        globals.home = mhHome;
        Path dispatcherPath = mhHome.resolve("dispatcher");
        Files.createDirectories(dispatcherPath);
        globals.dispatcherPath = dispatcherPath;
        return new VaultService(globals, pub);
    }

    @Test
    void test_putApiKey_publishesVaultEntryChangedEvent_actionPut(@TempDir Path tempPath) throws Exception {
        CapturingPublisher pub = new CapturingPublisher();
        VaultService service = newService(tempPath, pub);
        service.unlock("pass");
        long companyId = 42L;

        boolean ok = service.putApiKey(companyId, "openai", "sk-test");

        assertTrue(ok);
        assertEquals(1, pub.events.size());
        Object e = pub.events.get(0);
        assertInstanceOf(VaultEntryChangedEvent.class, e);
        VaultEntryChangedEvent evt = (VaultEntryChangedEvent) e;
        assertEquals(companyId, evt.companyId());
        assertEquals("openai", evt.keyCode());
        assertEquals(VaultEntryChangedEvent.ACTION_PUT, evt.action());
    }

    @Test
    void test_deleteApiKey_publishesVaultEntryChangedEvent_actionDelete(@TempDir Path tempPath) throws Exception {
        CapturingPublisher pub = new CapturingPublisher();
        VaultService service = newService(tempPath, pub);
        service.unlock("pass");
        long companyId = 42L;
        service.putApiKey(companyId, "openai", "sk-test");
        pub.events.clear();  // drop the put event; we're asserting on the delete event next.

        boolean ok = service.deleteApiKey(companyId, "openai");

        assertTrue(ok);
        assertEquals(1, pub.events.size());
        VaultEntryChangedEvent evt = (VaultEntryChangedEvent) pub.events.get(0);
        assertEquals(companyId, evt.companyId());
        assertEquals("openai", evt.keyCode());
        assertEquals(VaultEntryChangedEvent.ACTION_DELETE, evt.action());
    }

    @Test
    void test_putApiKey_locked_doesNotPublish(@TempDir Path tempPath) throws Exception {
        CapturingPublisher pub = new CapturingPublisher();
        VaultService service = newService(tempPath, pub);
        // intentionally do NOT unlock

        boolean ok = service.putApiKey(42L, "openai", "sk-test");

        assertFalse(ok);
        assertTrue(pub.events.isEmpty());
    }

    @Test
    void test_deleteApiKey_entryMissing_doesNotPublish(@TempDir Path tempPath) throws Exception {
        CapturingPublisher pub = new CapturingPublisher();
        VaultService service = newService(tempPath, pub);
        service.unlock("pass");

        boolean ok = service.deleteApiKey(42L, "never-existed");

        assertFalse(ok);
        assertTrue(pub.events.isEmpty());
    }
}
