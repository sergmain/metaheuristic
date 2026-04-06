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

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Spring Boot configuration for the Metaheuristic MCP Server.
 *
 * Activated only when both 'dispatcher' AND 'mcp' Spring profiles are active.
 *
 * The Spring AI MCP server starter (spring-ai-starter-mcp-server-webmvc) auto-configures
 * the Streamable HTTP endpoint based on application-mcp.properties. This configuration
 * class only contributes the explicit ToolCallbackProvider — required because Spring AI
 * 1.1.x annotation scanning misses @McpTool methods on AOP-proxied beans
 * (see https://github.com/spring-projects/spring-ai/issues/4882). MH services are
 * routinely wrapped by @Transactional / cache proxies, so we register the provider
 * by hand from the start to avoid the trap.
 *
 * @author Serge
 * Date: 4/6/2026
 */
@Configuration
@Profile("dispatcher & mcp")
@Slf4j
public class MhMcpServerConfig {

    /**
     * Explicitly register all @McpTool-annotated methods from MhMcpToolService.
     * Bypasses the annotation scanner that misses AOP-wrapped beans.
     */
    @Bean
    public ToolCallbackProvider mhMcpToolCallbackProvider(MhMcpToolService mhMcpToolService) {
        log.info("260.500 Registering Metaheuristic MCP tool callbacks for {}",
                mhMcpToolService.getClass().getName());
        return MethodToolCallbackProvider.builder()
                .toolObjects(mhMcpToolService)
                .build();
    }
}
