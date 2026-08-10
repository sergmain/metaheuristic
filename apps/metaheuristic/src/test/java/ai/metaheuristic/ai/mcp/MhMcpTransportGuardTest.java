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
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static ai.metaheuristic.ai.mcp.MhMcpToolDefinitions.transportGuarded;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression pin for the MCP transport boundary.
 *
 * <p>The SDK builds its JSON-RPC error frame from a thrown exception's getMessage() and asserts
 * it non-null, so a handler throwing an exception with a null message aborts the entire response
 * stream with "message must not be null" and a 500 - the real cause is demoted to a Suppressed:
 * frame and the caller is told nothing. A tool failure must come back as a CallToolResult with
 * isError=true, never as a protocol error.
 *
 * @author Serge
 * Date: 8/9/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class MhMcpTransportGuardTest {

    private static McpServerFeatures.SyncToolSpecification spec(
            String name, BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> handler) {
        Tool tool = Tool.builder().name(name).inputSchema(Map.of("type", "object")).build();
        return new McpServerFeatures.SyncToolSpecification(tool, handler);
    }

    private static String textOf(CallToolResult result) {
        assertEquals(1, result.content().size());
        return ((TextContent) result.content().get(0)).text();
    }

    /** A null-message exception must not escape to the transport. */
    @Test
    public void test_nullMessageThrowBecomesToolError() {
        var guarded = transportGuarded(spec("mh_reset_task", (e, r) -> {
            throw new IllegalStateException();
        }));

        CallToolResult result = guarded.callHandler().apply(null, null);

        assertNotNull(result);
        assertEquals(Boolean.TRUE, result.isError());
        String text = textOf(result);
        assertTrue(text.startsWith("01.260.280"), text);
        assertTrue(text.contains("mh_reset_task"), text);
        // getMessage() is null, so toString() carries the diagnosis instead of nothing
        assertTrue(text.contains("IllegalStateException"), text);
    }

    /** An NPE is the common real-world source of a null message. */
    @Test
    public void test_npeBecomesToolError() {
        var guarded = transportGuarded(spec("mh_get_variable_content", (e, r) -> {
            throw new NullPointerException();
        }));

        String text = textOf(guarded.callHandler().apply(null, null));

        assertFalse(text.isBlank());
        assertTrue(text.contains("mh_get_variable_content"), text);
    }

    /** A message that exists is passed through verbatim, not replaced by toString(). */
    @Test
    public void test_messageIsPreservedVerbatim() {
        var guarded = transportGuarded(spec("mh_get_task_info", (e, r) -> {
            throw new IllegalArgumentException("01.260.230 taskId not found");
        }));

        CallToolResult result = guarded.callHandler().apply(null, null);

        assertEquals(Boolean.TRUE, result.isError());
        assertTrue(textOf(result).contains("01.260.230 taskId not found"), textOf(result));
    }

    /** The happy path is untouched - the guard decorates, it does not wrap results. */
    @Test
    public void test_successfulResultPassesThroughUnchanged() {
        CallToolResult ok = CallToolResult.builder().addTextContent("done").isError(false).build();
        var guarded = transportGuarded(spec("mh_list_source_codes", (e, r) -> ok));

        CallToolResult result = guarded.callHandler().apply(null, null);

        assertSame(ok, result);
        assertFalse(result.isError());
    }

    /** Tool metadata must survive decoration, otherwise registration silently changes. */
    @Test
    public void test_toolMetadataIsPreserved() {
        Tool tool = Tool.builder().name("mh_stop_exec_context").title("Stop ExecContext")
                .inputSchema(Map.of("type", "object")).build();
        var original = new McpServerFeatures.SyncToolSpecification(tool, (e, r) -> null);

        var guarded = transportGuarded(original);

        assertSame(tool, guarded.tool());
        assertEquals("mh_stop_exec_context", guarded.tool().name());
        assertEquals("Stop ExecContext", guarded.tool().title());
    }

    /** No exception shape may yield a blank tool-error text. */
    @Test
    public void test_errorTextIsNeverBlank() {
        List<Throwable> nullMessaged = List.of(
                new NullPointerException(), new IllegalStateException(), new RuntimeException());

        nullMessaged.forEach(th -> {
            var guarded = transportGuarded(spec("t", (e, r) -> { throw new RuntimeException(th); }));
            String text = textOf(guarded.callHandler().apply(null, null));
            assertNotNull(text);
            assertFalse(text.isBlank(), th.getClass().getName());
        });
    }
}
