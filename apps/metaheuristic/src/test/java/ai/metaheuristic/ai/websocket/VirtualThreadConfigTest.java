/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.websocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests verifying that virtual thread configuration is properly set via application.properties,
 * making manual TaskExecutor bean definitions for WebSocket unnecessary.
 *
 * Context: Prior to Spring Boot 3.3, when the 'websocket' profile was active, MH manually
 * defined two TaskExecutor beans (taskExecutor and taskScheduler) with virtual threads enabled.
 * Starting in Spring Boot 3.2, spring.threads.virtual.enabled=true auto-configures virtual-thread
 * executors. In Spring Boot 3.3, these auto-configured executors are automatically registered
 * on the WebSocket ChannelRegistration.
 *
 * Having BOTH the manual beans AND the auto-configuration caused a bean conflict:
 * - Two competing executor beans with the same logical purpose
 * - The manual beans could override the auto-configured ones unpredictably
 * - The WebSocket channel might bind to the wrong executor
 *
 * The fix was to remove the manual beans and rely on spring.threads.virtual.enabled=true.
 * This test verifies that the property is set correctly.
 *
 * @author Sergio Lissner
 * Date: 3/20/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class VirtualThreadConfigTest {

    /**
     * Verify that spring.threads.virtual.enabled=true is set in application.properties.
     * This property is what Spring Boot 3.3+ uses to auto-configure virtual-thread executors
     * for @Async, @Scheduled, and WebSocket channel registration.
     */
    @Test
    public void test_virtualThreadsEnabledInProperties() throws IOException {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            assertNotNull(is, "application.properties not found on classpath");
            props.load(is);
        }
        String value = props.getProperty("spring.threads.virtual.enabled");
        assertEquals("true", value,
                "spring.threads.virtual.enabled must be 'true' for auto-configured virtual thread executors. " +
                "This replaces the manual taskExecutor/taskScheduler beans that were previously needed under the 'websocket' profile.");
    }

    /**
     * Verify that virtual threads are actually available at runtime (Java 21+).
     * The MH project requires Java 25, so this should always pass.
     */
    @Test
    public void test_virtualThreadsAvailableAtRuntime() {
        Thread vt = Thread.ofVirtual().unstarted(() -> {});
        assertTrue(vt.isVirtual(), "Virtual threads must be available at runtime");
    }
}
