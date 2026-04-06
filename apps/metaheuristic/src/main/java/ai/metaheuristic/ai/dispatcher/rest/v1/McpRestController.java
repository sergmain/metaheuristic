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

package ai.metaheuristic.ai.dispatcher.rest.v1;

import ai.metaheuristic.ai.mcp.MhMcpToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Health/info endpoint for the Metaheuristic MCP Server.
 *
 * NOTE: this is NOT the MCP protocol endpoint itself — that one is provided by Spring AI's
 * auto-configured Streamable HTTP transport at /rest/v1/mcp (configured in
 * application-mcp.properties). This controller exposes a sibling /rest/v1/mcp-info GET
 * endpoint so humans (and curl) can quickly check that the MCP server is running and
 * see which tools are registered.
 *
 * Activated only when both 'dispatcher' AND 'mcp' Spring profiles are active.
 *
 * @author Serge
 * Date: 4/6/2026
 */
@RestController
@RequestMapping("/rest/v1/mcp-info")
@Profile("dispatcher & mcp")
@CrossOrigin
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class McpRestController {

    private final ToolCallbackProvider mhMcpToolCallbackProvider;
    private final MhMcpToolService mhMcpToolService;

    public record ToolDescriptor(String name, String description) {}

    public record McpInfoDto(
            String serverName,
            String endpoint,
            int toolCount,
            List<ToolDescriptor> tools,
            String toolServiceClass
    ) {}

    @GetMapping
    public McpInfoDto info() {
        ToolCallback[] callbacks = mhMcpToolCallbackProvider.getToolCallbacks();
        List<ToolDescriptor> tools = List.of(callbacks).stream()
                .map(cb -> new ToolDescriptor(
                        cb.getToolDefinition().name(),
                        cb.getToolDefinition().description()))
                .toList();
        return new McpInfoDto(
                "metaheuristic-mcp-server",
                "/rest/v1/mcp",
                callbacks.length,
                tools,
                mhMcpToolService.getClass().getName()
        );
    }
}
