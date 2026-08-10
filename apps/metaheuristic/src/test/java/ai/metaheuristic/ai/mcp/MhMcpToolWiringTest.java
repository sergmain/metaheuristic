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
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the pairing of each static tool definition to its handler method reference.
 *
 * <p>Tool metadata and behaviour are declared separately - {@code xxxTool(this::handleXxx)} - and
 * every handler shares one signature, so swapping two method references compiles cleanly and the
 * server would answer the wrong tool. Nothing but a test catches that.
 *
 * <p>The probe: each handler resolves its required argument first, before touching any service, so
 * calling it with no arguments reports the parameter name the handler actually asked for. That name
 * must be the one its own tool declares. All services are null here - reaching one is itself a
 * wiring failure, and is reported as such rather than as an NPE.
 *
 * @author Serge
 * Date: 8/10/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class MhMcpToolWiringTest {

    /** Tool name -> the argument its handler must ask for. Kept explicit so a swap is visible here. */
    private static final Map<String, String> REQUIRED_ARG = new LinkedHashMap<>() {{
        put("mh_get_variable_info", "variableId");
        put("mh_get_variable_content", "variableId");
        put("mh_start_exec_context", "execContextId");
        put("mh_stop_exec_context", "execContextId");
        put("mh_get_task_info", "taskId");
        put("mh_reset_task", "taskId");
        put("mh_get_exec_context_info", "execContextId");
        put("mh_get_exec_context_graph", "execContextGraphId");
        put("mh_get_exec_context_task_state", "execContextTaskStateId");
        put("mh_get_exec_context_variable_state", "execContextVariableStateId");
        put("mh_get_source_code", "sourceCodeId");
    }};

    /** Takes no arguments, so the probe above cannot reach it. */
    private static final String NO_ARG_TOOL = "mh_list_source_codes";

    private static List<McpServerFeatures.SyncToolSpecification> specs() {
        return new MhMcpToolDefinitions(null, null, null, null, null, null, null, null, null)
                .getAllToolSpecifications();
    }

    private static String textOf(CallToolResult result) {
        return result.content().stream()
                .map(c -> ((TextContent) c).text())
                .collect(Collectors.joining());
    }

    /**
     * Tool metadata is constant data, so it is built once at class-init and handed out by reference.
     * Two calls must yield the very same {@link io.modelcontextprotocol.spec.McpSchema.Tool} instances -
     * equal-but-distinct objects would mean the declarations are being rebuilt per call again.
     */
    @Test
    public void test_toolMetadataIsBuiltOnceNotPerCall() {
        Map<String, Tool> first = specs().stream()
                .collect(Collectors.toMap(s -> s.tool().name(), McpServerFeatures.SyncToolSpecification::tool));

        specs().forEach(s -> assertSame(first.get(s.tool().name()), s.tool(), s.tool().name()));
    }

    @Test
    public void test_everyToolIsRegisteredExactlyOnce() {
        List<String> names = specs().stream().map(s -> s.tool().name()).toList();

        assertEquals(names.size(), Set.copyOf(names).size(), "duplicate tool registered: " + names);
        Set<String> expected = new java.util.HashSet<>(REQUIRED_ARG.keySet());
        expected.add(NO_ARG_TOOL);
        assertEquals(expected, Set.copyOf(names));
    }

    /** The wiring pin: a handler paired with the wrong tool asks for the wrong argument. */
    @Test
    public void test_eachHandlerIsPairedWithItsOwnTool() {
        specs().stream()
                .filter(s -> !NO_ARG_TOOL.equals(s.tool().name()))
                .forEach(s -> {
                    String toolName = s.tool().name();
                    String expectedArg = REQUIRED_ARG.get(toolName);
                    assertNotNull(expectedArg, "unknown tool, update this test: " + toolName);

                    CallToolResult result = s.callHandler()
                            .apply(null, new CallToolRequest(toolName, Map.of()));

                    assertEquals(Boolean.TRUE, result.isError(), toolName);
                    String text = textOf(result);
                    assertTrue(text.contains("Required parameter '" + expectedArg + "' is missing"),
                            toolName + " is wired to a handler that asked for something else: " + text);
                });
    }

    /** The no-argument tool still reaches a handler rather than failing to resolve one. */
    @Test
    public void test_noArgToolIsWired() {
        McpServerFeatures.SyncToolSpecification spec = specs().stream()
                .filter(s -> NO_ARG_TOOL.equals(s.tool().name()))
                .findFirst()
                .orElseThrow();

        CallToolResult result = spec.callHandler()
                .apply(null, new CallToolRequest(NO_ARG_TOOL, Map.of()));

        // no required argument to probe, so it runs on to the null repository - reaching it proves
        // a handler is attached, and the guard turns the failure into a readable tool error
        assertEquals(Boolean.TRUE, result.isError());
        assertFalse(textOf(result).isBlank());
    }
}
