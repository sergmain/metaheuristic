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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextGraphRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextTaskStateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskResetService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP tool definitions for the Metaheuristic debug/tracing server.
 *
 * Mirrors the MHDG MCP server pattern (apps/metaheuristic/legal/.../MhdgMcpToolDefinitions):
 * tools are built explicitly via the low-level MCP Java SDK
 * (io.modelcontextprotocol.sdk:mcp 0.17.2), no Spring AI annotation magic.
 *
 * Activated only when both 'dispatcher' AND 'mcp' Spring profiles are active.
 *
 * 9 tools total — read-mostly access to MH internals plus a few control operations:
 *
 *   mh_get_variable_info           — metadata for an internal Variable by id
 *   mh_get_variable_content        — content of an internal Variable, truncated to N bytes
 *   mh_start_exec_context          — transition an ExecContext to STARTED
 *   mh_stop_exec_context           — transition an ExecContext to STOPPED
 *   mh_get_task_info               — Task info by id
 *   mh_reset_task                  — reset a Task (delegates to TaskResetService)
 *   mh_get_exec_context_info       — ExecContext info by id
 *   mh_get_exec_context_graph      — ExecContextGraph by id (raw params YAML, static Process DAG)
 *   mh_get_exec_context_task_state — ExecContextTaskState by id (raw params YAML, dynamic Task DAG)
 *
 * @author Serge
 * Date: 4/6/2026
 */
@Component
@Profile("dispatcher & mcp")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class MhMcpToolDefinitions {

    public static final int DEFAULT_VARIABLE_CONTENT_LIMIT = 1024;
    public static final int MAX_VARIABLE_CONTENT_LIMIT = 65536;

    private final VariableTxService variableTxService;
    private final TaskRepository taskRepository;
    private final TaskResetService taskResetService;
    private final ExecContextCache execContextCache;
    private final ExecContextTopLevelService execContextTopLevelService;
    private final ExecContextGraphRepository execContextGraphRepository;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // ==================== DTOs ====================

    public record VariableInfoDto(
            Long id,
            String name,
            Long execContextId,
            String taskContextId,
            boolean inited,
            boolean nullified,
            @Nullable Long variableBlobId,
            @Nullable String filename,
            @Nullable String uploadTs,
            @Nullable String params
    ) {}

    public record VariableContentDto(
            Long id,
            String name,
            int returnedBytes,
            boolean truncated,
            @Nullable String content,
            @Nullable String error
    ) {}

    public record TaskInfoDto(
            Long id,
            Long execContextId,
            @Nullable Long coreId,
            int execState,
            String execStateName,
            int completed,
            int resultReceived,
            @Nullable Long assignedOn,
            @Nullable Long updatedOn,
            @Nullable Long completedOn,
            @Nullable String functionExecResultsExcerpt
    ) {}

    public record ExecContextInfoDto(
            Long id,
            @Nullable Long sourceCodeId,
            @Nullable Long companyId,
            @Nullable Long accountId,
            int state,
            String stateName,
            @Nullable Long createdOn,
            @Nullable Long completedOn,
            @Nullable Long execContextGraphId,
            @Nullable Long execContextTaskStateId,
            @Nullable Long execContextVariableStateId,
            @Nullable Long rootExecContextId,
            boolean valid,
            @Nullable String errorMessages
    ) {}

    public record ExecContextGraphDto(
            Long id,
            @Nullable Long execContextId,
            @Nullable Long createdOn,
            @Nullable String params
    ) {}

    public record ExecContextTaskStateDto(
            Long id,
            @Nullable Long execContextId,
            @Nullable Long createdOn,
            @Nullable String params
    ) {}

    public record OperationResultDto(
            boolean ok,
            String message
    ) {}

    // ==================== Build all tool specifications ====================

    public List<McpServerFeatures.SyncToolSpecification> getAllToolSpecifications() {
        return List.of(
                getVariableInfoTool(),
                getVariableContentTool(),
                startExecContextTool(),
                stopExecContextTool(),
                getTaskInfoTool(),
                resetTaskTool(),
                getExecContextInfoTool(),
                getExecContextGraphTool(),
                getExecContextTaskStateTool()
        );
    }

    // ==================== Tool 1: get variable info ====================

    private McpServerFeatures.SyncToolSpecification getVariableInfoTool() {
        Map<String, Object> props = new HashMap<>();
        props.put("variableId", Map.of("type", "integer", "description", "Numeric id of the Variable"));

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("mh_get_variable_info")
                        .title("Get Variable Info")
                        .description("Get metadata about an internal Variable by its numeric id. Returns name, "
                                + "execContextId, taskContextId, inited/nullified flags, blobId, filename, and params. "
                                + "Does NOT return the variable content — use mh_get_variable_content for that.")
                        .inputSchema(new McpSchema.JsonSchema("object", props, List.of("variableId"), false, null, null))
                        .build())
                .callHandler((exchange, request) -> {
                    Long variableId = getRequiredLong(request.arguments(), "variableId");
                    log.info("260.020 MCP getVariableInfo({})", variableId);
                    Variable v = variableTxService.getVariable(variableId);
                    if (v == null) {
                        return errorResult("Variable #" + variableId + " not found");
                    }
                    return toCallToolResult(new VariableInfoDto(
                            v.id, v.name, v.execContextId, v.taskContextId,
                            v.inited, v.nullified, v.variableBlobId, v.filename,
                            v.uploadTs == null ? null : v.uploadTs.toString(),
                            v.getParams()
                    ));
                })
                .build();
    }

    // ==================== Tool 2: get variable content (with size limit) ====================

    private McpServerFeatures.SyncToolSpecification getVariableContentTool() {
        Map<String, Object> props = new HashMap<>();
        props.put("variableId", Map.of("type", "integer", "description", "Numeric id of the Variable"));
        props.put("maxBytes", Map.of("type", "integer", "description",
                "Maximum number of bytes to return (default 1024, max 65536)"));

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("mh_get_variable_content")
                        .title("Get Variable Content")
                        .description("Get the textual content of an internal Variable by its numeric id, "
                                + "truncated to maxBytes (default 1024, max 65536). Returns the content as a UTF-8 "
                                + "string and a 'truncated' flag indicating whether the original was longer.")
                        .inputSchema(new McpSchema.JsonSchema("object", props, List.of("variableId"), false, null, null))
                        .build())
                .callHandler((exchange, request) -> {
                    Map<String, Object> arguments = request.arguments();
                    Long variableId = getRequiredLong(arguments, "variableId");
                    Integer maxBytesArg = getOptionalInt(arguments, "maxBytes");
                    int limit = maxBytesArg == null
                            ? DEFAULT_VARIABLE_CONTENT_LIMIT
                            : Math.min(Math.max(maxBytesArg, 1), MAX_VARIABLE_CONTENT_LIMIT);
                    log.info("260.040 MCP getVariableContent({}, limit={})", variableId, limit);

                    Variable v = variableTxService.getVariable(variableId);
                    if (v == null) {
                        return errorResult("Variable #" + variableId + " not found");
                    }
                    if (!v.inited || v.nullified) {
                        return toCallToolResult(new VariableContentDto(v.id, v.name, 0, false, null,
                                "Variable is not inited or is nullified (inited=" + v.inited + ", nullified=" + v.nullified + ")"));
                    }
                    try {
                        String full = variableTxService.getVariableDataAsString(variableId);
                        if (full == null) {
                            return toCallToolResult(new VariableContentDto(v.id, v.name, 0, false, null, "Variable content is null"));
                        }
                        boolean truncated = full.length() > limit;
                        String returned = truncated ? full.substring(0, limit) : full;
                        return toCallToolResult(new VariableContentDto(v.id, v.name, returned.length(), truncated, returned, null));
                    }
                    catch (Throwable th) {
                        log.error("260.060 Error reading variable #" + variableId + " content", th);
                        return toCallToolResult(new VariableContentDto(v.id, v.name, 0, false, null,
                                "Error reading content: " + th.getMessage()));
                    }
                })
                .build();
    }

    // ==================== Tool 3a: start execContext ====================

    private McpServerFeatures.SyncToolSpecification startExecContextTool() {
        Map<String, Object> props = new HashMap<>();
        props.put("execContextId", Map.of("type", "integer", "description", "Numeric id of the ExecContext"));

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("mh_start_exec_context")
                        .title("Start ExecContext")
                        .description("Start a specific ExecContext by id. Transitions the ExecContext to STARTED state.")
                        .inputSchema(new McpSchema.JsonSchema("object", props, List.of("execContextId"), false, null, null))
                        .build())
                .callHandler((exchange, request) -> {
                    Long execContextId = getRequiredLong(request.arguments(), "execContextId");
                    log.info("260.080 MCP startExecContext({})", execContextId);
                    return toCallToolResult(changeExecContextState(execContextId, EnumsApi.ExecContextState.STARTED));
                })
                .build();
    }

    // ==================== Tool 3b: stop execContext ====================

    private McpServerFeatures.SyncToolSpecification stopExecContextTool() {
        Map<String, Object> props = new HashMap<>();
        props.put("execContextId", Map.of("type", "integer", "description", "Numeric id of the ExecContext"));

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("mh_stop_exec_context")
                        .title("Stop ExecContext")
                        .description("Stop a specific ExecContext by id. Transitions the ExecContext to STOPPED state.")
                        .inputSchema(new McpSchema.JsonSchema("object", props, List.of("execContextId"), false, null, null))
                        .build())
                .callHandler((exchange, request) -> {
                    Long execContextId = getRequiredLong(request.arguments(), "execContextId");
                    log.info("260.100 MCP stopExecContext({})", execContextId);
                    return toCallToolResult(changeExecContextState(execContextId, EnumsApi.ExecContextState.STOPPED));
                })
                .build();
    }

    private OperationResultDto changeExecContextState(Long execContextId, EnumsApi.ExecContextState newState) {
        ExecContextImpl ec = execContextCache.findById(execContextId, true);
        if (ec == null) {
            return new OperationResultDto(false, "ExecContext #" + execContextId + " not found");
        }
        OperationStatusRest status = execContextTopLevelService.execContextTargetState(execContextId, newState, ec.companyId);
        boolean ok = status.status == EnumsApi.OperationStatus.OK;
        String msg = ok
                ? "ExecContext #" + execContextId + " transitioned to " + newState
                : String.join("; ", status.getErrorMessagesAsList());
        return new OperationResultDto(ok, msg);
    }

    // ==================== Tool 4: get task info ====================

    private McpServerFeatures.SyncToolSpecification getTaskInfoTool() {
        Map<String, Object> props = new HashMap<>();
        props.put("taskId", Map.of("type", "integer", "description", "Numeric id of the Task"));

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("mh_get_task_info")
                        .title("Get Task Info")
                        .description("Get info about a Task by id: execContextId, current execState, completion flags, "
                                + "assignment timestamps, and a short excerpt of functionExecResults if present.")
                        .inputSchema(new McpSchema.JsonSchema("object", props, List.of("taskId"), false, null, null))
                        .build())
                .callHandler((exchange, request) -> {
                    Long taskId = getRequiredLong(request.arguments(), "taskId");
                    log.info("260.120 MCP getTaskInfo({})", taskId);
                    TaskImpl task = taskRepository.findByIdReadOnly(taskId);
                    if (task == null) {
                        return errorResult("Task #" + taskId + " not found");
                    }
                    String excerpt = task.functionExecResults == null
                            ? null
                            : (task.functionExecResults.length() > 512
                                ? task.functionExecResults.substring(0, 512) + "..."
                                : task.functionExecResults);
                    return toCallToolResult(new TaskInfoDto(
                            task.id, task.execContextId, task.coreId,
                            task.execState, EnumsApi.TaskExecState.from(task.execState).name(),
                            task.completed, task.resultReceived,
                            task.assignedOn, task.updatedOn, task.completedOn,
                            excerpt
                    ));
                })
                .build();
    }

    // ==================== Tool 5: reset task ====================

    private McpServerFeatures.SyncToolSpecification resetTaskTool() {
        Map<String, Object> props = new HashMap<>();
        props.put("taskId", Map.of("type", "integer", "description", "Numeric id of the Task"));

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("mh_reset_task")
                        .title("Reset Task")
                        .description("Reset a specific Task by id. Resets the task to INIT state and, if the parent "
                                + "ExecContext was FINISHED, transitions it back to STARTED. Delegates to "
                                + "TaskResetService.resetTaskAndExecContext which acquires the required write locks.")
                        .inputSchema(new McpSchema.JsonSchema("object", props, List.of("taskId"), false, null, null))
                        .build())
                .callHandler((exchange, request) -> {
                    Long taskId = getRequiredLong(request.arguments(), "taskId");
                    log.info("260.140 MCP resetTask({})", taskId);
                    TaskImpl task = taskRepository.findByIdReadOnly(taskId);
                    if (task == null) {
                        return toCallToolResult(new OperationResultDto(false, "Task #" + taskId + " not found"));
                    }
                    Long execContextId = task.execContextId;
                    try {
                        taskResetService.resetTaskAndExecContext(execContextId, taskId);
                        return toCallToolResult(new OperationResultDto(true,
                                "Task #" + taskId + " reset in execContext #" + execContextId));
                    }
                    catch (Throwable th) {
                        log.error("260.160 Error resetting task #" + taskId, th);
                        return toCallToolResult(new OperationResultDto(false, "Error resetting task: " + th.getMessage()));
                    }
                })
                .build();
    }

    // ==================== Tool 6: get exec context info ====================

    private McpServerFeatures.SyncToolSpecification getExecContextInfoTool() {
        Map<String, Object> props = new HashMap<>();
        props.put("execContextId", Map.of("type", "integer", "description", "Numeric id of the ExecContext"));

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("mh_get_exec_context_info")
                        .title("Get ExecContext Info")
                        .description("Get info about an ExecContext by id: state, sourceCodeId, companyId, accountId, "
                                + "graph/task-state/variable-state ids, root id, and validity flag.")
                        .inputSchema(new McpSchema.JsonSchema("object", props, List.of("execContextId"), false, null, null))
                        .build())
                .callHandler((exchange, request) -> {
                    Long execContextId = getRequiredLong(request.arguments(), "execContextId");
                    log.info("260.180 MCP getExecContextInfo({})", execContextId);
                    ExecContextImpl ec = execContextCache.findById(execContextId, true);
                    if (ec == null) {
                        return errorResult("ExecContext #" + execContextId + " not found");
                    }
                    SourceCodeApiData.ExecContextResult extended = execContextTopLevelService.getExecContextExtended(execContextId);
                    boolean valid = extended != null && !extended.isErrorMessages();
                    String errorMessages = (extended != null && extended.isErrorMessages())
                            ? String.join("; ", extended.getErrorMessagesAsList())
                            : null;
                    return toCallToolResult(new ExecContextInfoDto(
                            ec.id, ec.sourceCodeId, ec.companyId, ec.accountId,
                            ec.state, EnumsApi.ExecContextState.toState(ec.state).name(),
                            ec.createdOn, ec.completedOn,
                            ec.execContextGraphId, ec.execContextTaskStateId, ec.execContextVariableStateId,
                            ec.rootExecContextId,
                            valid, errorMessages
                    ));
                })
                .build();
    }

    // ==================== Tool 7: get exec context graph ====================

    private McpServerFeatures.SyncToolSpecification getExecContextGraphTool() {
        Map<String, Object> props = new HashMap<>();
        props.put("execContextGraphId", Map.of("type", "integer", "description", "Numeric id of the ExecContextGraph"));

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("mh_get_exec_context_graph")
                        .title("Get ExecContext Graph")
                        .description("Get an ExecContextGraph by its id (NOT by execContextId — use "
                                + "mh_get_exec_context_info first to find the execContextGraphId). Returns the raw "
                                + "params YAML representing the static Process DAG.")
                        .inputSchema(new McpSchema.JsonSchema("object", props, List.of("execContextGraphId"), false, null, null))
                        .build())
                .callHandler((exchange, request) -> {
                    Long execContextGraphId = getRequiredLong(request.arguments(), "execContextGraphId");
                    log.info("260.200 MCP getExecContextGraph({})", execContextGraphId);
                    Optional<ExecContextGraph> opt = execContextGraphRepository.findById(execContextGraphId);
                    if (opt.isEmpty()) {
                        return errorResult("ExecContextGraph #" + execContextGraphId + " not found");
                    }
                    ExecContextGraph g = opt.get();
                    return toCallToolResult(new ExecContextGraphDto(g.id, g.execContextId, g.createdOn, g.getParams()));
                })
                .build();
    }

    // ==================== Tool 8: get exec context task state ====================

    private McpServerFeatures.SyncToolSpecification getExecContextTaskStateTool() {
        Map<String, Object> props = new HashMap<>();
        props.put("execContextTaskStateId", Map.of("type", "integer", "description", "Numeric id of the ExecContextTaskState"));

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("mh_get_exec_context_task_state")
                        .title("Get ExecContext Task State")
                        .description("Get an ExecContextTaskState by its id (NOT by execContextId — use "
                                + "mh_get_exec_context_info first to find the execContextTaskStateId). Returns the raw "
                                + "params YAML representing the dynamic Task execution state.")
                        .inputSchema(new McpSchema.JsonSchema("object", props, List.of("execContextTaskStateId"), false, null, null))
                        .build())
                .callHandler((exchange, request) -> {
                    Long execContextTaskStateId = getRequiredLong(request.arguments(), "execContextTaskStateId");
                    log.info("260.220 MCP getExecContextTaskState({})", execContextTaskStateId);
                    Optional<ExecContextTaskState> opt = execContextTaskStateRepository.findById(execContextTaskStateId);
                    if (opt.isEmpty()) {
                        return errorResult("ExecContextTaskState #" + execContextTaskStateId + " not found");
                    }
                    ExecContextTaskState s = opt.get();
                    return toCallToolResult(new ExecContextTaskStateDto(s.id, s.execContextId, s.createdOn, s.getParams()));
                })
                .build();
    }

    // ==================== Utility methods ====================

    private static Long getRequiredLong(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Required parameter '" + key + "' is missing");
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(value.toString());
    }

    @Nullable
    private static Integer getOptionalInt(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private CallToolResult toCallToolResult(Object result) {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            return CallToolResult.builder()
                    .content(List.of(new TextContent(json)))
                    .isError(false)
                    .build();
        }
        catch (JsonProcessingException e) {
            log.error("260.240 Error serializing tool result", e);
            return CallToolResult.builder()
                    .content(List.of(new TextContent("Error: " + e.getMessage())))
                    .isError(true)
                    .build();
        }
    }

    private static CallToolResult errorResult(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
