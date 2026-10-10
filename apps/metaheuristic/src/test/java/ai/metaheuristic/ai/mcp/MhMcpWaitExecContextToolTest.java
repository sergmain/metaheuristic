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

package ai.metaheuristic.ai.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The argument contract of {@code mh_wait_exec_context}, called by name through its registered spec.
 *
 * <p>Every argument is resolved before any service is reached, so with all services null the handler's own
 * rejections are what comes back - by their error codes. A call that passes them reaches the null ExecContext cache,
 * which the transport guard reports as a tool error of its own; that is how acceptance is told apart from rejection.
 * The wait itself is covered by {@link ExecContextWaitUtilsTest}.
 *
 * @author Serge
 * Date: 10/10/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class MhMcpWaitExecContextToolTest {

    private static final String TOOL = "mh_wait_exec_context";

    private static CallToolResult call(Map<String, Object> arguments) {
        final McpServerFeatures.SyncToolSpecification spec = new MhMcpToolDefinitions(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)
                .getAllToolSpecifications().stream()
                .filter(s -> TOOL.equals(s.tool().name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("not registered: " + TOOL));
        return spec.callHandler().apply(null, new CallToolRequest(TOOL, arguments));
    }

    private static String textOf(CallToolResult result) {
        return result.content().stream().map(c -> ((TextContent) c).text()).collect(Collectors.joining());
    }

    @Test
    public void test_untilIsRequired() {
        final CallToolResult r = call(Map.of("execContextId", 42L));

        assertEquals(Boolean.TRUE, r.isError());
        assertTrue(textOf(r).contains("Required parameter 'until' is missing"), textOf(r));
    }

    @Test
    public void test_unknownUntil_isRejectedByName() {
        final CallToolResult r = call(Map.of("execContextId", 42L, "until", "FINISHED"));

        assertEquals(Boolean.TRUE, r.isError());
        final String text = textOf(r);
        assertTrue(text.contains("01.260.790"), text);
        assertTrue(text.contains("[TERMINAL, ANY_ERROR, STATE_CHANGED]"), text);
        assertTrue(text.contains("was: FINISHED"), text);
    }

    @Test
    public void test_untilIsMatchedIgnoringCase() {
        final CallToolResult r = call(Map.of("execContextId", 42L, "until", "any_error"));

        // accepted: the call goes on to the (null) ExecContext cache instead of coming back as the rejection
        assertEquals(Boolean.TRUE, r.isError());
        assertFalse(textOf(r).contains("01.260.790"), textOf(r));
    }

    @Test
    public void test_maxSecondsBelowOne_isRejected() {
        final CallToolResult r = call(Map.of("execContextId", 42L, "until", "TERMINAL", "maxSeconds", 0));

        assertEquals(Boolean.TRUE, r.isError());
        final String text = textOf(r);
        assertTrue(text.contains("01.260.780"), text);
        assertTrue(text.contains("was: 0"), text);
    }

    @Test
    public void test_maxSecondsAboveTheCap_isRejected() {
        final int tooLong = MhMcpToolDefinitions.MAX_WAIT_SECONDS + 1;
        final CallToolResult r = call(Map.of("execContextId", 42L, "until", "TERMINAL", "maxSeconds", tooLong));

        assertEquals(Boolean.TRUE, r.isError());
        final String text = textOf(r);
        assertTrue(text.contains("01.260.780"), text);
        assertTrue(text.contains("was: " + tooLong), text);
    }

    @Test
    public void test_maxSecondsAtTheCap_isAccepted() {
        final CallToolResult r = call(Map.of("execContextId", 42L, "until", "TERMINAL",
                "maxSeconds", MhMcpToolDefinitions.MAX_WAIT_SECONDS));

        assertEquals(Boolean.TRUE, r.isError());
        assertFalse(textOf(r).contains("01.260.780"), textOf(r));
    }
}
