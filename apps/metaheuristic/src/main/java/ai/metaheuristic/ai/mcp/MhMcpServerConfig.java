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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Spring Boot configuration for the Metaheuristic MCP Server over HTTP
 * (Streamable HTTP transport, MCP spec 2025-03-26).
 *
 * Exposes the MCP server as a regular HTTP endpoint on the already-running
 * Spring Boot dispatcher application — no separate process, no STDIO.
 *
 * Endpoint: /rest/v1/mcp
 *
 * Activated only when both 'dispatcher' AND 'mcp' Spring profiles are active.
 *
 * === Purpose ===
 *
 * Debug / tracing access to MH internal state for external MCP clients
 * (Claude Desktop, Claude Code, MCP Inspector). Read-mostly tools for
 * Variables, Tasks, ExecContexts, ExecContextGraphs and ExecContextTaskStates,
 * plus start/stop/reset operations.
 *
 * === Claude Desktop / Claude Code configuration ===
 *
 * Claude Code (.mcp.json):
 * {
 *   "mcpServers": {
 *     "metaheuristic": {
 *       "type": "http",
 *       "url": "http://localhost:PORT/rest/v1/mcp"
 *     }
 *   }
 * }
 *
 * Claude Desktop (claude_desktop_config.json), via mcp-remote bridge:
 * {
 *   "mcpServers": {
 *     "metaheuristic": {
 *       "command": "npx",
 *       "args": ["-y", "mcp-remote", "http://localhost:PORT/rest/v1/mcp"]
 *     }
 *   }
 * }
 *
 * @author Serge
 * Date: 4/6/2026
 */
@Configuration
@Profile("dispatcher & mcp")
@Slf4j
public class MhMcpServerConfig {

    public static final String MCP_ENDPOINT = "/rest/v1/mcp";

    private McpSyncServer mcpSyncServer;

    @Bean
    public RouterFunction<ServerResponse> mhMcpRouterFunction(
            @Autowired MhMcpToolDefinitions toolDefinitions,
            @Autowired ObjectMapper objectMapper) {

        WebMvcStreamableServerTransportProvider transportProvider = WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper(objectMapper))
                .mcpEndpoint(MCP_ENDPOINT)
                .build();

        mcpSyncServer = McpServer.sync(transportProvider)
                .serverInfo("metaheuristic-mcp-server", "1.0.0")
                .capabilities(ServerCapabilities.builder()
                        .tools(true)
                        .logging()
                        .build())
                .tools(toolDefinitions.getAllToolSpecifications())
                .build();

        log.info("260.500 Metaheuristic MCP Server created with {} tools on {}",
                toolDefinitions.getAllToolSpecifications().size(), MCP_ENDPOINT);

        return transportProvider.getRouterFunction();
    }

    @PreDestroy
    public void shutdown() {
        if (mcpSyncServer != null) {
            mcpSyncServer.close();
            log.info("260.520 Metaheuristic MCP Server shut down");
        }
    }
}
