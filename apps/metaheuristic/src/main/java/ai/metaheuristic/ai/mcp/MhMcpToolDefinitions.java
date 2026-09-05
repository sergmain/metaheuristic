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
import ai.metaheuristic.ai.dispatcher.bundle.BundleService;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextVariableState;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextGraphRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextTaskStateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextVariableStateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskResetService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BundleData;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.account.UserContext;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * MCP tool definitions for the Metaheuristic debug/tracing server.
 *
 * Tools are built explicitly via the low-level MCP Java SDK
 * (io.modelcontextprotocol.sdk:mcp 0.17.2), no Spring AI annotation magic.
 *
 * Activated only when both 'dispatcher' AND 'mcp' Spring profiles are active.
 *
 * 13 tools total — read-mostly access to MH internals plus a few control operations:
 *
 *   mh_get_variable_info               — metadata for an internal Variable by id
 *   mh_get_variable_content            — content of an internal Variable, truncated to N bytes
 *   mh_start_exec_context              — transition an ExecContext to STARTED
 *   mh_stop_exec_context               — transition an ExecContext to STOPPED
 *   mh_get_task_info                   — Task info by id
 *   mh_reset_task                      — reset a Task (delegates to TaskResetService)
 *   mh_get_exec_context_info           — ExecContext info by id
 *   mh_get_exec_context_graph          — ExecContextGraph by id (raw params YAML, static Process DAG)
 *   mh_get_exec_context_task_state     — ExecContextTaskState by id (raw params YAML, dynamic Task DAG)
 *   mh_get_exec_context_variable_state — ExecContextVariableState by id (raw params YAML, dynamic Variable state)
 *   mh_list_source_codes               — list all SourceCodes with general info (id, uid, companyId, latch, valid)
 *   mh_get_source_code                 — full SourceCode by id, including params YAML (truncated to maxParamsBytes)
 *   mh_import_bundle_from_git          — import a bundle straight from a git repo url + path
 *
 * <p>Error code prefix: {@code 01.260.} (unique to this class).
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
    public static final int DEFAULT_SOURCE_CODE_PARAMS_LIMIT = 65536;
    public static final int MAX_SOURCE_CODE_PARAMS_LIMIT = 1048576;

    private final VariableTxService variableTxService;
    private final TaskRepository taskRepository;
    private final TaskResetService taskResetService;
    private final ExecContextCache execContextCache;
    private final ExecContextTopLevelService execContextTopLevelService;
    private final ExecContextGraphRepository execContextGraphRepository;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;
    private final ExecContextVariableStateRepository execContextVariableStateRepository;
    private final SourceCodeRepository sourceCodeRepository;
    private final BundleService bundleService;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    public record ExecContextVariableStateDto(
            Long id,
            @Nullable Long execContextId,
            @Nullable Long createdOn,
            @Nullable String params
    ) {}

    public record OperationResultDto(
            boolean ok,
            String message
    ) {}

    public record ImportBundleResultDto(
            boolean ok,
            String repo,
            String path,
            Long companyId,
            List<String> errorMessages,
            List<String> infoMessages
    ) {}

    /**
     * The MCP server carries no authenticated principal, so the caller states the company it is importing
     * into. Deliberately NOT a DispatcherContext: that one needs real Account and Company entities, and
     * fabricating them to satisfy a getter would be worse than saying plainly that this is the only
     * identity the tool has.
     */
    private record McpUserContext(Long accountId, Long companyId, String username) implements UserContext {
        @Override public Long getAccountId() { return accountId; }
        @Override public Long getCompanyId() { return companyId; }
        @Override public String getUsername() { return username; }
    }

    public record SourceCodeDto(
            Long id,
            @Nullable Integer version,
            @Nullable Long companyId,
            @Nullable String uid,
            long createdOn,
            boolean valid,
            @Nullable String latch,
            int paramsBytes,
            boolean truncated,
            @Nullable String params
    ) {}

    /**
     * Transport-boundary guard - applied to EVERY tool spec in {@link #getAllToolSpecifications()}.
     *
     * <p>The MCP SDK builds the JSON-RPC error frame straight from a thrown exception's
     * {@code getMessage()} and asserts it non-null ({@code McpSchema$JSONRPCResponse$JSONRPCError}).
     * An exception whose message is null - any NPE, {@code IllegalStateException()},
     * {@code NotImplementedException()} - therefore aborts the whole response stream with
     * "message must not be null" and a 500, and the caller learns neither which tool failed
     * nor why: the real cause survives only as a {@code Suppressed:} frame in the server log.
     *
     * <p>Per MCP semantics a tool failure is a {@code CallToolResult} with {@code isError=true},
     * not a protocol error - the caller is meant to read it and correct itself. No handler may
     * throw to the transport. Decorates the spec record's handler; handler bodies are untouched.
     */
    static McpServerFeatures.SyncToolSpecification transportGuarded(McpServerFeatures.SyncToolSpecification spec) {
        final var delegate = spec.callHandler();
        final String toolName = spec.tool().name();
        return new McpServerFeatures.SyncToolSpecification(spec.tool(), (exchange, request) -> {
            try {
                return delegate.apply(exchange, request);
            }
            catch (Throwable th) {
                log.error("01.260.270 tool '{}' failed, returning it as an isError CallToolResult", toolName, th);
                final String msg = th.getMessage() == null ? th.toString() : th.getMessage();
                return CallToolResult.builder()
                        .addTextContent("01.260.280 ERROR in '" + toolName + "': " + msg)
                        .isError(true)
                        .build();
            }
        });
    }

    // ==================== Tool 13: import a bundle straight from a git repo ====================

    private static final Tool IMPORT_BUNDLE_FROM_GIT_TOOL = Tool.builder("mh_import_bundle_from_git",
                    objectSchema(
                            Map.of(
                                    "repo", Map.of("type", "string",
                                            "description", "Url of the git repository holding the bundle, e.g. https://github.com/sergmain/metaheuristic-assets.git"),
                                    "path", Map.of("type", "string",
                                            "description", "Path inside the repo to the directory containing mh-bundle.yaml. Required: one repo may hold several bundles at different paths."),
                                    "companyId", Map.of("type", "integer",
                                            "description", "Unique id of the company to import into"),
                                    "accountId", Map.of("type", "integer",
                                            "description", "Optional account id recorded as the importer. Defaults to 0.")),
                            List.of("repo", "path", "companyId")))
            .title("Import a bundle from git")
            .description("Import a bundle - Functions, SourceCodes, api and auth - directly from a git repository, "
                    + "without packaging and uploading a zip first. The dispatcher clones the repo's DEFAULT branch "
                    + "shallowly, reads mh-bundle.yaml at the given path, and processes it through the same pipeline an "
                    + "uploaded zip goes through. No branch and no revision are accepted: delivery always takes the "
                    + "current state of the descriptors. This says nothing about a Function's sourcing - a Function whose "
                    + "artifacts sit beside its own mh-function.yaml is packaged as usual; only a Function declaring its "
                    + "own git block is git-sourced.")
            .build();

    private CallToolResult handleImportBundleFromGit(McpSyncServerExchange exchange, CallToolRequest request) {
        final Map<String, Object> arguments = request.arguments();
        final String repo = getRequiredString(arguments, "repo");
        final String path = getRequiredString(arguments, "path");
        final Long companyId = getRequiredLong(arguments, "companyId");
        final Integer accountId = getOptionalInt(arguments, "accountId");

        log.info("01.260.300 MCP importBundleFromGit(repo={}, path={}, companyId={})", repo, path, companyId);

        // branch and commit are left unset on purpose: delivery clones whatever remote HEAD points at,
        // which is the repo's default branch - master for some repos, main for others
        final GitInfo gitInfo = new GitInfo(repo, "", "", path);
        final UserContext context = new McpUserContext(
                accountId == null ? 0L : accountId.longValue(), companyId, "mcp");

        final BundleData.UploadingStatus status = bundleService.uploadFromGit(gitInfo, context);

        final List<String> errors = status.getErrorMessagesAsList();
        return toCallToolResult(new ImportBundleResultDto(
                errors.isEmpty(), repo, path, companyId, errors, status.getInfoMessagesAsList()));
    }

    // ==================== Build all tool specifications ====================

    public List<McpServerFeatures.SyncToolSpecification> getAllToolSpecifications() {
        return Stream.of(
                new McpServerFeatures.SyncToolSpecification(GET_VARIABLE_INFO_TOOL, this::handleGetVariableInfo),
                new McpServerFeatures.SyncToolSpecification(GET_VARIABLE_CONTENT_TOOL, this::handleGetVariableContent),
                new McpServerFeatures.SyncToolSpecification(START_EXEC_CONTEXT_TOOL, this::handleStartExecContext),
                new McpServerFeatures.SyncToolSpecification(STOP_EXEC_CONTEXT_TOOL, this::handleStopExecContext),
                new McpServerFeatures.SyncToolSpecification(GET_TASK_INFO_TOOL, this::handleGetTaskInfo),
                new McpServerFeatures.SyncToolSpecification(RESET_TASK_TOOL, this::handleResetTask),
                new McpServerFeatures.SyncToolSpecification(GET_EXEC_CONTEXT_INFO_TOOL, this::handleGetExecContextInfo),
                new McpServerFeatures.SyncToolSpecification(GET_EXEC_CONTEXT_GRAPH_TOOL, this::handleGetExecContextGraph),
                new McpServerFeatures.SyncToolSpecification(GET_EXEC_CONTEXT_TASK_STATE_TOOL, this::handleGetExecContextTaskState),
                new McpServerFeatures.SyncToolSpecification(GET_EXEC_CONTEXT_VARIABLE_STATE_TOOL, this::handleGetExecContextVariableState),
                new McpServerFeatures.SyncToolSpecification(LIST_SOURCE_CODES_TOOL, this::handleListSourceCodes),
                new McpServerFeatures.SyncToolSpecification(GET_SOURCE_CODE_TOOL, this::handleGetSourceCode),
                new McpServerFeatures.SyncToolSpecification(IMPORT_BUNDLE_FROM_GIT_TOOL, this::handleImportBundleFromGit)
        ).map(MhMcpToolDefinitions::transportGuarded).toList();
    }

    // ==================== Tool 1: get variable info ====================

    private static final Tool GET_VARIABLE_INFO_TOOL = Tool.builder("mh_get_variable_info",
                    objectSchema(
                            Map.of("variableId", Map.of("type", "integer", "description", "Numeric id of the Variable")),
                            List.of("variableId")))
            .title("Get Variable Info")
            .description("Get metadata about an internal Variable by its numeric id. Returns name, "
                    + "execContextId, taskContextId, inited/nullified flags, blobId, filename, and params. "
                    + "Does NOT return the variable content — use mh_get_variable_content for that.")
            .build();

    private CallToolResult handleGetVariableInfo(McpSyncServerExchange exchange, CallToolRequest request) {
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
    }

    // ==================== Tool 2: get variable content (with size limit) ====================

    private static final Tool GET_VARIABLE_CONTENT_TOOL = Tool.builder("mh_get_variable_content",
                    objectSchema(
                            Map.of("variableId", Map.of("type", "integer", "description", "Numeric id of the Variable"),
                                    "maxBytes", Map.of("type", "integer", "description",
                                            "Maximum number of bytes to return (default 1024, max 65536)")),
                            List.of("variableId")))
            .title("Get Variable Content")
            .description("Get the textual content of an internal Variable by its numeric id, "
                    + "truncated to maxBytes (default 1024, max 65536). Returns the content as a UTF-8 "
                    + "string and a 'truncated' flag indicating whether the original was longer.")
            .build();

    private CallToolResult handleGetVariableContent(McpSyncServerExchange exchange, CallToolRequest request) {
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
    }

    // ==================== Tool 3a: start execContext ====================

    private static final Tool START_EXEC_CONTEXT_TOOL = Tool.builder("mh_start_exec_context",
                    objectSchema(
                            Map.of("execContextId", Map.of("type", "integer", "description", "Numeric id of the ExecContext")),
                            List.of("execContextId")))
            .title("Start ExecContext")
            .description("Start a specific ExecContext by id. Transitions the ExecContext to STARTED state.")
            .build();

    private CallToolResult handleStartExecContext(McpSyncServerExchange exchange, CallToolRequest request) {
        Long execContextId = getRequiredLong(request.arguments(), "execContextId");
        log.info("260.080 MCP startExecContext({})", execContextId);
        return toCallToolResult(changeExecContextState(execContextId, EnumsApi.ExecContextState.STARTED));
    }

    // ==================== Tool 3b: stop execContext ====================

    private static final Tool STOP_EXEC_CONTEXT_TOOL = Tool.builder("mh_stop_exec_context",
                    objectSchema(
                            Map.of("execContextId", Map.of("type", "integer", "description", "Numeric id of the ExecContext")),
                            List.of("execContextId")))
            .title("Stop ExecContext")
            .description("Stop a specific ExecContext by id. Transitions the ExecContext to STOPPED state.")
            .build();

    private CallToolResult handleStopExecContext(McpSyncServerExchange exchange, CallToolRequest request) {
        Long execContextId = getRequiredLong(request.arguments(), "execContextId");
        log.info("260.100 MCP stopExecContext({})", execContextId);
        return toCallToolResult(changeExecContextState(execContextId, EnumsApi.ExecContextState.STOPPED));
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

    private static final Tool GET_TASK_INFO_TOOL = Tool.builder("mh_get_task_info",
                    objectSchema(
                            Map.of("taskId", Map.of("type", "integer", "description", "Numeric id of the Task")),
                            List.of("taskId")))
            .title("Get Task Info")
            .description("Get info about a Task by id: execContextId, current execState, completion flags, "
                    + "assignment timestamps, and a short excerpt of functionExecResults if present.")
            .build();

    private CallToolResult handleGetTaskInfo(McpSyncServerExchange exchange, CallToolRequest request) {
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
    }

    // ==================== Tool 5: reset task ====================

    private static final Tool RESET_TASK_TOOL = Tool.builder("mh_reset_task",
                    objectSchema(
                            Map.of("taskId", Map.of("type", "integer", "description", "Numeric id of the Task")),
                            List.of("taskId")))
            .title("Reset Task")
            .description("Reset a specific Task by id. Resets the task to INIT state and, if the parent "
                    + "ExecContext was FINISHED, transitions it back to STARTED. Delegates to "
                    + "TaskResetService.resetTaskAndExecContext which acquires the required write locks.")
            .build();

    private CallToolResult handleResetTask(McpSyncServerExchange exchange, CallToolRequest request) {
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
    }

    // ==================== Tool 6: get exec context info ====================

    private static final Tool GET_EXEC_CONTEXT_INFO_TOOL = Tool.builder("mh_get_exec_context_info",
                    objectSchema(
                            Map.of("execContextId", Map.of("type", "integer", "description", "Numeric id of the ExecContext")),
                            List.of("execContextId")))
            .title("Get ExecContext Info")
            .description("Get ExecContext info by id. This is the polling endpoint for execution "
                    + "completion: call repeatedly (every 3–5 seconds) after the run is launched "
                    + "until stateName is a terminal state — FINISHED (successful completion), ERROR, "
                    + "STOPPED, or DOESNT_EXIST. Any other stateName (STARTED, NONE, ...) means the "
                    + "run is still in progress and you must keep polling. Returns: state (numeric), "
                    + "stateName, sourceCodeId, companyId, accountId, graph/task-state/variable-state ids, "
                    + "root id, createdOn/completedOn timestamps, and validity flag.")
            .build();

    private CallToolResult handleGetExecContextInfo(McpSyncServerExchange exchange, CallToolRequest request) {
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
    }

    // ==================== Tool 7: get exec context graph ====================

    private static final Tool GET_EXEC_CONTEXT_GRAPH_TOOL = Tool.builder("mh_get_exec_context_graph",
                    objectSchema(
                            Map.of("execContextGraphId", Map.of("type", "integer", "description", "Numeric id of the ExecContextGraph")),
                            List.of("execContextGraphId")))
            .title("Get ExecContext Graph")
            .description("Get an ExecContextGraph by its id (NOT by execContextId — use "
                    + "mh_get_exec_context_info first to find the execContextGraphId). Returns the raw "
                    + "params YAML representing the static Process DAG.")
            .build();

    private CallToolResult handleGetExecContextGraph(McpSyncServerExchange exchange, CallToolRequest request) {
        Long execContextGraphId = getRequiredLong(request.arguments(), "execContextGraphId");
        log.info("260.200 MCP getExecContextGraph({})", execContextGraphId);
        Optional<ExecContextGraph> opt = execContextGraphRepository.findById(execContextGraphId);
        if (opt.isEmpty()) {
            return errorResult("ExecContextGraph #" + execContextGraphId + " not found");
        }
        ExecContextGraph g = opt.get();
        return toCallToolResult(new ExecContextGraphDto(g.id, g.execContextId, g.createdOn, g.getParams()));
    }

    // ==================== Tool 8: get exec context task state ====================

    private static final Tool GET_EXEC_CONTEXT_TASK_STATE_TOOL = Tool.builder("mh_get_exec_context_task_state",
                    objectSchema(
                            Map.of("execContextTaskStateId", Map.of("type", "integer", "description", "Numeric id of the ExecContextTaskState")),
                            List.of("execContextTaskStateId")))
            .title("Get ExecContext Task State")
            .description("Get an ExecContextTaskState by its id (NOT by execContextId — use "
                    + "mh_get_exec_context_info first to find the execContextTaskStateId). Returns the raw "
                    + "params YAML representing the dynamic Task execution state.")
            .build();

    private CallToolResult handleGetExecContextTaskState(McpSyncServerExchange exchange, CallToolRequest request) {
        Long execContextTaskStateId = getRequiredLong(request.arguments(), "execContextTaskStateId");
        log.info("260.220 MCP getExecContextTaskState({})", execContextTaskStateId);
        Optional<ExecContextTaskState> opt = execContextTaskStateRepository.findById(execContextTaskStateId);
        if (opt.isEmpty()) {
            return errorResult("ExecContextTaskState #" + execContextTaskStateId + " not found");
        }
        ExecContextTaskState s = opt.get();
        return toCallToolResult(new ExecContextTaskStateDto(s.id, s.execContextId, s.createdOn, s.getParams()));
    }

    // ==================== Tool 9: get exec context variable state ====================

    private static final Tool GET_EXEC_CONTEXT_VARIABLE_STATE_TOOL = Tool.builder("mh_get_exec_context_variable_state",
                    objectSchema(
                            Map.of("execContextVariableStateId", Map.of("type", "integer", "description", "Numeric id of the ExecContextVariableState")),
                            List.of("execContextVariableStateId")))
            .title("Get ExecContext Variable State")
            .description("Get an ExecContextVariableState by its id (NOT by execContextId \u2014 use "
                    + "mh_get_exec_context_info first to find the execContextVariableStateId). Returns the raw "
                    + "params YAML representing the dynamic Variable state (per-variable inited/nullified "
                    + "status, blob ids, task context ids).")
            .build();

    private CallToolResult handleGetExecContextVariableState(McpSyncServerExchange exchange, CallToolRequest request) {
        Long execContextVariableStateId = getRequiredLong(request.arguments(), "execContextVariableStateId");
        log.info("260.230 MCP getExecContextVariableState({})", execContextVariableStateId);
        Optional<ExecContextVariableState> opt = execContextVariableStateRepository.findById(execContextVariableStateId);
        if (opt.isEmpty()) {
            return errorResult("ExecContextVariableState #" + execContextVariableStateId + " not found");
        }
        ExecContextVariableState v = opt.get();
        return toCallToolResult(new ExecContextVariableStateDto(v.id, v.execContextId, v.createdOn, v.getParams()));
    }

    // ==================== Tool 10: list source codes ====================

    private static final Tool LIST_SOURCE_CODES_TOOL = Tool.builder("mh_list_source_codes",
                    objectSchema(Map.of(), List.of()))
            .title("List SourceCodes")
            .description("List all SourceCodes in the database with general info: id, uid, "
                    + "companyId, latch, and valid flag. Returns all rows across all companies "
                    + "(no companyId filter). Use this to discover available SourceCodes and their "
                    + "uids before calling tools that require a sourceCodeUidPrefix.")
            .build();

    private CallToolResult handleListSourceCodes(McpSyncServerExchange exchange, CallToolRequest request) {
        log.info("260.250 MCP listSourceCodes()");
        List<SourceCodeData.SourceCodeListItem> items = sourceCodeRepository.findAllAsListItems();
        return toCallToolResult(items);
    }

    // ==================== Tool 11: get source code (full entity, including params YAML) ====================

    private static final Tool GET_SOURCE_CODE_TOOL = Tool.builder("mh_get_source_code",
                    objectSchema(
                            Map.of("sourceCodeId", Map.of("type", "integer", "description", "Numeric id of the SourceCode (MH_SOURCE_CODE.ID)"),
                                    "maxParamsBytes", Map.of("type", "integer", "description",
                                            "Maximum number of bytes of the params YAML to return (default 65536, max 1048576). "
                                            + "Use mh_list_source_codes first to discover available ids.")),
                            List.of("sourceCodeId")))
            .title("Get SourceCode")
            .description("Get a SourceCode by id, including the full params YAML body "
                    + "(the .mhsc/.mhscp source). Returns id, version, companyId, uid, createdOn, "
                    + "valid flag, latch, params (truncated to maxParamsBytes), and a 'truncated' flag. "
                    + "Use this to inspect what's actually deployed on the dispatcher when the on-disk "
                    + "source and the deployed bundle have drifted apart.")
            .build();

    private CallToolResult handleGetSourceCode(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> arguments = request.arguments();
        Long sourceCodeId = getRequiredLong(arguments, "sourceCodeId");
        Integer maxBytesArg = getOptionalInt(arguments, "maxParamsBytes");
        int limit = maxBytesArg == null
                ? DEFAULT_SOURCE_CODE_PARAMS_LIMIT
                : Math.min(Math.max(maxBytesArg, 1), MAX_SOURCE_CODE_PARAMS_LIMIT);
        log.info("260.260 MCP getSourceCode({}, limit={})", sourceCodeId, limit);

        SourceCodeImpl sc = sourceCodeRepository.findByIdNullable(sourceCodeId);
        if (sc == null) {
            return errorResult("SourceCode #" + sourceCodeId + " not found");
        }
        String fullParams = sc.getParams();
        String returnedParams;
        boolean truncated;
        int returnedBytes;
        if (fullParams == null) {
            returnedParams = null;
            truncated = false;
            returnedBytes = 0;
        }
        else if (fullParams.length() > limit) {
            returnedParams = fullParams.substring(0, limit);
            truncated = true;
            returnedBytes = returnedParams.length();
        }
        else {
            returnedParams = fullParams;
            truncated = false;
            returnedBytes = fullParams.length();
        }
        return toCallToolResult(new SourceCodeDto(
                sc.id, sc.version, sc.companyId, sc.uid, sc.createdOn, sc.valid, sc.latch,
                returnedBytes, truncated, returnedParams
        ));
    }

    // ==================== Utility methods ====================

    private static String getRequiredString(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Required parameter '" + key + "' is missing or blank");
        }
        return value.toString().strip();
    }

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

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private CallToolResult toCallToolResult(Object result) {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            return CallToolResult.builder()
                    .addTextContent(json)
                    .isError(false)
                    .build();
        }
        catch (JacksonException e) {
            log.error("260.240 Error serializing tool result", e);
            return CallToolResult.builder()
                    .addTextContent("Error: " + e.getMessage())
                    .isError(true)
                    .build();
        }
    }

    private static CallToolResult errorResult(String message) {
        return CallToolResult.builder()
                .addTextContent(message)
                .isError(true)
                .build();
    }
}
