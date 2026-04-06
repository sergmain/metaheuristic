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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Metaheuristic MCP Server tool implementations.
 *
 * Provides debug/tracing access to MH internal state for external MCP clients
 * (Claude Desktop, Claude Code, MCP Inspector, ...) over the Streamable HTTP
 * endpoint configured in application-mcp.properties.
 *
 * Activated only when both 'dispatcher' AND 'mcp' Spring profiles are active.
 *
 * @author Serge
 * Date: 4/6/2026
 */
@Service
@Profile("dispatcher & mcp")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class MhMcpToolService {

    public static final int DEFAULT_VARIABLE_CONTENT_LIMIT = 1024;
    public static final int MAX_VARIABLE_CONTENT_LIMIT = 65536;

    private final VariableTxService variableTxService;
    private final TaskRepository taskRepository;
    private final TaskResetService taskResetService;
    private final ExecContextCache execContextCache;
    private final ExecContextTopLevelService execContextTopLevelService;
    private final ExecContextGraphRepository execContextGraphRepository;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;

    // ==================== DTOs (record-based, FP style) ====================

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

    // ==================== Tool 1: get variable info ====================

    @McpTool(
            name = "mh_get_variable_info",
            description = "Get metadata about an internal Variable by its numeric id. Returns name, " +
                    "execContextId, taskContextId, inited/nullified flags, blobId, filename, and params. " +
                    "Does NOT return the variable content — use mh_get_variable_content for that."
    )
    public VariableInfoDto getVariableInfo(
            @McpToolParam(description = "Numeric id of the Variable", required = true) Long variableId) {

        log.info("260.020 MCP getVariableInfo({})", variableId);
        Variable v = variableTxService.getVariable(variableId);
        if (v == null) {
            throw new IllegalArgumentException("Variable #" + variableId + " not found");
        }
        return new VariableInfoDto(
                v.id, v.name, v.execContextId, v.taskContextId,
                v.inited, v.nullified, v.variableBlobId, v.filename,
                v.uploadTs == null ? null : v.uploadTs.toString(),
                v.getParams()
        );
    }

    // ==================== Tool 2: get variable content (with size limit) ====================

    @McpTool(
            name = "mh_get_variable_content",
            description = "Get the textual content of an internal Variable by its numeric id, " +
                    "truncated to maxBytes (default 1024, max 65536). Returns the content as a UTF-8 " +
                    "string and a 'truncated' flag indicating whether the original was longer."
    )
    public VariableContentDto getVariableContent(
            @McpToolParam(description = "Numeric id of the Variable", required = true) Long variableId,
            @McpToolParam(description = "Maximum number of bytes to return (default 1024, max 65536)", required = false) @Nullable Integer maxBytes) {

        int limit = maxBytes == null ? DEFAULT_VARIABLE_CONTENT_LIMIT : Math.min(Math.max(maxBytes, 1), MAX_VARIABLE_CONTENT_LIMIT);
        log.info("260.040 MCP getVariableContent({}, limit={})", variableId, limit);

        Variable v = variableTxService.getVariable(variableId);
        if (v == null) {
            throw new IllegalArgumentException("Variable #" + variableId + " not found");
        }
        if (!v.inited || v.nullified) {
            return new VariableContentDto(v.id, v.name, 0, false, null,
                    "Variable is not inited or is nullified (inited=" + v.inited + ", nullified=" + v.nullified + ")");
        }
        try {
            String full = variableTxService.getVariableDataAsString(variableId);
            if (full == null) {
                return new VariableContentDto(v.id, v.name, 0, false, null, "Variable content is null");
            }
            boolean truncated = full.length() > limit;
            String returned = truncated ? full.substring(0, limit) : full;
            return new VariableContentDto(v.id, v.name, returned.length(), truncated, returned, null);
        }
        catch (Throwable th) {
            log.error("260.060 Error reading variable #" + variableId + " content", th);
            return new VariableContentDto(v.id, v.name, 0, false, null, "Error reading content: " + th.getMessage());
        }
    }

    // ==================== Tool 3a: stop execContext ====================

    @McpTool(
            name = "mh_stop_exec_context",
            description = "Stop a specific ExecContext by id. Transitions the ExecContext to STOPPED state."
    )
    public OperationResultDto stopExecContext(
            @McpToolParam(description = "Numeric id of the ExecContext", required = true) Long execContextId) {

        log.info("260.080 MCP stopExecContext({})", execContextId);
        return changeExecContextState(execContextId, EnumsApi.ExecContextState.STOPPED);
    }

    // ==================== Tool 3b: start execContext ====================

    @McpTool(
            name = "mh_start_exec_context",
            description = "Start a specific ExecContext by id. Transitions the ExecContext to STARTED state."
    )
    public OperationResultDto startExecContext(
            @McpToolParam(description = "Numeric id of the ExecContext", required = true) Long execContextId) {

        log.info("260.100 MCP startExecContext({})", execContextId);
        return changeExecContextState(execContextId, EnumsApi.ExecContextState.STARTED);
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

    @McpTool(
            name = "mh_get_task_info",
            description = "Get info about a Task by id: execContextId, current execState, completion flags, " +
                    "assignment timestamps, and a short excerpt of functionExecResults if present."
    )
    public TaskInfoDto getTaskInfo(
            @McpToolParam(description = "Numeric id of the Task", required = true) Long taskId) {

        log.info("260.120 MCP getTaskInfo({})", taskId);
        TaskImpl task = taskRepository.findByIdReadOnly(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task #" + taskId + " not found");
        }
        String excerpt = task.functionExecResults == null
                ? null
                : (task.functionExecResults.length() > 512
                    ? task.functionExecResults.substring(0, 512) + "..."
                    : task.functionExecResults);
        return new TaskInfoDto(
                task.id, task.execContextId, task.coreId,
                task.execState, EnumsApi.TaskExecState.from(task.execState).name(),
                task.completed, task.resultReceived,
                task.assignedOn, task.updatedOn, task.completedOn,
                excerpt
        );
    }

    // ==================== Tool 5: reset task ====================

    @McpTool(
            name = "mh_reset_task",
            description = "Reset a specific Task by id. Resets the task to INIT state and, if the parent " +
                    "ExecContext was FINISHED, transitions it back to STARTED. Delegates to " +
                    "TaskResetService.resetTaskAndExecContext which acquires the required write locks."
    )
    public OperationResultDto resetTask(
            @McpToolParam(description = "Numeric id of the Task", required = true) Long taskId) {

        log.info("260.140 MCP resetTask({})", taskId);
        TaskImpl task = taskRepository.findByIdReadOnly(taskId);
        if (task == null) {
            return new OperationResultDto(false, "Task #" + taskId + " not found");
        }
        Long execContextId = task.execContextId;
        try {
            taskResetService.resetTaskAndExecContext(execContextId, taskId);
            return new OperationResultDto(true, "Task #" + taskId + " reset in execContext #" + execContextId);
        }
        catch (Throwable th) {
            log.error("260.160 Error resetting task #" + taskId, th);
            return new OperationResultDto(false, "Error resetting task: " + th.getMessage());
        }
    }

    // ==================== Tool 6: get exec context info ====================

    @McpTool(
            name = "mh_get_exec_context_info",
            description = "Get info about an ExecContext by id: state, sourceCodeId, companyId, accountId, " +
                    "graph/task-state/variable-state ids, root id, and validity flag."
    )
    public ExecContextInfoDto getExecContextInfo(
            @McpToolParam(description = "Numeric id of the ExecContext", required = true) Long execContextId) {

        log.info("260.180 MCP getExecContextInfo({})", execContextId);
        ExecContextImpl ec = execContextCache.findById(execContextId, true);
        if (ec == null) {
            throw new IllegalArgumentException("ExecContext #" + execContextId + " not found");
        }
        SourceCodeApiData.ExecContextResult extended = execContextTopLevelService.getExecContextExtended(execContextId);
        boolean valid = extended != null && !extended.isErrorMessages();
        String errorMessages = (extended != null && extended.isErrorMessages())
                ? String.join("; ", extended.getErrorMessagesAsList())
                : null;
        return new ExecContextInfoDto(
                ec.id, ec.sourceCodeId, ec.companyId, ec.accountId,
                ec.state, EnumsApi.ExecContextState.toState(ec.state).name(),
                ec.createdOn, ec.completedOn,
                ec.execContextGraphId, ec.execContextTaskStateId, ec.execContextVariableStateId,
                ec.rootExecContextId,
                valid, errorMessages
        );
    }

    // ==================== Tool 7: get exec context graph ====================

    @McpTool(
            name = "mh_get_exec_context_graph",
            description = "Get an ExecContextGraph by its id (NOT by execContextId — use " +
                    "mh_get_exec_context_info first to find the execContextGraphId). Returns the raw " +
                    "params YAML representing the static Process DAG."
    )
    public ExecContextGraphDto getExecContextGraph(
            @McpToolParam(description = "Numeric id of the ExecContextGraph", required = true) Long execContextGraphId) {

        log.info("260.200 MCP getExecContextGraph({})", execContextGraphId);
        Optional<ExecContextGraph> opt = execContextGraphRepository.findById(execContextGraphId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("ExecContextGraph #" + execContextGraphId + " not found");
        }
        ExecContextGraph g = opt.get();
        return new ExecContextGraphDto(g.id, g.execContextId, g.createdOn, g.getParams());
    }

    // ==================== Tool 8: get exec context task state ====================

    @McpTool(
            name = "mh_get_exec_context_task_state",
            description = "Get an ExecContextTaskState by its id (NOT by execContextId — use " +
                    "mh_get_exec_context_info first to find the execContextTaskStateId). Returns the raw " +
                    "params YAML representing the dynamic Task execution state."
    )
    public ExecContextTaskStateDto getExecContextTaskState(
            @McpToolParam(description = "Numeric id of the ExecContextTaskState", required = true) Long execContextTaskStateId) {

        log.info("260.220 MCP getExecContextTaskState({})", execContextTaskStateId);
        Optional<ExecContextTaskState> opt = execContextTaskStateRepository.findById(execContextTaskStateId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("ExecContextTaskState #" + execContextTaskStateId + " not found");
        }
        ExecContextTaskState s = opt.get();
        return new ExecContextTaskStateDto(s.id, s.execContextId, s.createdOn, s.getParams());
    }
}
