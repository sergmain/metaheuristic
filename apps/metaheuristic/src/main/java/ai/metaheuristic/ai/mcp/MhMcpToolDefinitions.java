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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph;
import ai.metaheuristic.ai.dispatcher.bundle.BundleService;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextVariableState;
import ai.metaheuristic.ai.dispatcher.beans.MetaStorage;
import ai.metaheuristic.ai.dispatcher.beans.MetaStorageSynthetic;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.data.ExecutionGateViewData;
import ai.metaheuristic.ai.dispatcher.execution_gate.ExecutionGateService;
import ai.metaheuristic.ai.dispatcher.monitoring.GateMonitoring;
import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageService;
import ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageSyntheticService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextGraphRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextTaskStateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextVariableStateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.MetaStorageRepository;
import ai.metaheuristic.ai.dispatcher.repositories.MetaStorageSyntheticRepository;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskResetService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BundleData;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
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
import org.springframework.security.core.Authentication;
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
 * 19 tools total — read-mostly access to MH internals plus a few control operations:
 *
 *   mh_get_variable_info               — metadata for an internal Variable by id
 *   mh_get_variable_content            — content of an internal Variable, truncated to N bytes
 *   mh_create_exec_context             — create an ExecContext from a SourceCode and produce its Tasks
 *   mh_exec_context_target_state       — move an ExecContext, and its related ones, to STARTED or STOPPED
 *   mh_get_task_info                   — Task info by id
 *   mh_reset_task                      — reset a Task (delegates to TaskResetService)
 *   mh_get_exec_context_info           — ExecContext info by id
 *   mh_get_exec_context_graph          — ExecContextGraph by id (raw params YAML, static Process DAG)
 *   mh_get_exec_context_task_state     — ExecContextTaskState by id (raw params YAML, dynamic Task DAG)
 *   mh_get_exec_context_variable_state — ExecContextVariableState by id (raw params YAML, dynamic Variable state)
 *   mh_list_source_codes               — list all SourceCodes with general info (id, uid, companyId, latch, valid)
 *   mh_list_processors                 — list Processors with liveness, blacklist reason and declared envs
 *   mh_execution_gate_status           — what work is being withheld from Processors, and why
 *   mh_get_source_code                 — full SourceCode by id, including params YAML (truncated to maxParamsBytes)
 *   mh_import_bundle_from_git          — import a bundle straight from a git repo url + path
 *   mh_get_meta_storage_record         — one meta storage record by row id, from MH_META_STORAGE or MH_META_STORAGE_SYNTHETIC
 *   mh_select_meta_storage_record      — the same record addressed by its natural key (companyId, type, recKey)
 *   mh_delete_meta_storage_record      — delete one record addressed by its natural key
 *   mh_list_meta_storage_rec_keys      — every recKey for one (companyId, type), bodies unread
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
    private final ExecContextCreatorTopLevelService execContextCreatorTopLevelService;
    private final ProcessorTopLevelService processorTopLevelService;
    private final UserContextService userContextService;
    private final ExecutionGateService executionGateService;
    private final GateMonitoring gateMonitoring;
    private final MetaStorageRepository metaStorageRepository;
    private final MetaStorageSyntheticRepository metaStorageSyntheticRepository;
    private final MetaStorageService metaStorageService;
    private final MetaStorageSyntheticService metaStorageSyntheticService;

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

    public record ProcessorCoreDto(
            Long id,
            String code,
            boolean busy
    ) {}

    /**
     * Quota state, because {@code not_enough_quotas} is one of the gate's rejection reasons and nothing
     * else exposes what a Processor's limits actually are. Pairs with envCodes the same way: the gate
     * names the reason, this says which Processor can satisfy it.
     */
    public record ProcessorQuotasDto(
            int limit,
            int defaultValue,
            boolean disabled,
            List<String> tags
    ) {}

    public record ProcessorDto(
            Long id,
            @Nullable String description,
            @Nullable String ip,
            @Nullable String host,
            boolean active,
            boolean functionProblem,
            boolean blacklisted,
            @Nullable String blacklistReason,
            long blacklistedForMills,
            long lastSeen,
            int taskParamsVersion,
            @Nullable String os,
            @Nullable List<String> envCodes,
            @Nullable ProcessorQuotasDto quotas,
            @Nullable String currDir,
            @Nullable List<String> errors,
            List<ProcessorCoreDto> cores
    ) {}

    public record CreateExecContextResultDto(
            boolean ok,
            @Nullable Long execContextId,
            Long sourceCodeId,
            Long companyId,
            @Nullable String sourceCodeUid,
            @Nullable Integer state,
            @Nullable String stateName,
            List<String> errorMessages,
            List<String> infoMessages
    ) {}

    /**
     * The caller's own identity, taken from the authenticated principal.
     *
     * <p>❗ NOT a companyId argument. An earlier version let the caller name the company it was writing
     * into, which is wrong twice over: it is an authorization decision handed to the party being
     * authorized, and in practice callers passed {@code Consts.MANAGEMENT_COMPANY_ID}, so imported
     * SourceCodes landed in the management company and were invisible on the source-codes page of the
     * company that had asked for them. The company a caller may write to is a property OF the caller.
     *
     * <p>{@code MhMcpServerConfig} captures the Spring Security {@code Authentication} on the servlet
     * thread and stashes it in the transport context precisely so a handler can do this.
     */
    private UserContext userContextOf(McpSyncServerExchange exchange) {
        final Object authentication = exchange==null ? null : exchange.transportContext().get("authentication");
        if (!(authentication instanceof Authentication auth) || !auth.isAuthenticated()) {
            throw new IllegalStateException(
                    "01.260.380 this tool writes on behalf of a company and the request carries no authenticated "
                    + "principal to take one from");
        }
        return userContextService.getContext(auth);
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
     * One meta storage row, from either table.
     *
     * <p>{@code synthetic} reports which table it was read from, so a result copied out of its call
     * still names its own source - the two tables carry identical columns and independent id
     * sequences, so the row alone cannot say where it came from.
     *
     * <p>❗ {@code body} is returned exactly as stored. MH never parsed it on the way in and does not
     * parse it here: its encoding belongs to whoever wrote the record.
     */
    public record MetaStorageRecordDto(
            boolean synthetic,
            Long id,
            @Nullable Integer version,
            @Nullable Long companyId,
            @Nullable String type,
            @Nullable String recKey,
            long gen,
            long updatedAt,
            @Nullable String body
    ) {}

    /**
     * The recKeys of one {@code (companyId, type)}, and nothing else.
     *
     * <p>❗ Carries no bodies by design. This is the selection step that feeds a batch splitter: the
     * caller decides which keys it wants and fetches those payloads afterwards, so shipping every
     * body here would defeat the point of the query.
     */
    public record MetaStorageRecKeysDto(
            boolean synthetic,
            Long companyId,
            String type,
            int count,
            List<String> recKeys
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
                                            "description", "Path inside the repo to the directory containing mh-bundle.yaml. Required: one repo may hold several bundles at different paths.")),
                            List.of("repo", "path")))
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
        final UserContext context = userContextOf(exchange);
        final Long companyId = context.getCompanyId();

        log.info("01.260.300 MCP importBundleFromGit(repo={}, path={}, companyId={})", repo, path, companyId);

        // branch and commit are left unset on purpose: delivery clones whatever remote HEAD points at,
        // which is the repo's default branch - master for some repos, main for others
        final GitInfo gitInfo = new GitInfo(repo, "", "", path);

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
                new McpServerFeatures.SyncToolSpecification(LIST_PROCESSORS_TOOL, this::handleListProcessors),
                new McpServerFeatures.SyncToolSpecification(EXECUTION_GATE_STATUS_TOOL, this::handleExecutionGateStatus),
                new McpServerFeatures.SyncToolSpecification(CREATE_EXEC_CONTEXT_TOOL, this::handleCreateExecContext),
                new McpServerFeatures.SyncToolSpecification(EXEC_CONTEXT_TARGET_STATE_TOOL, this::handleExecContextTargetState),
                new McpServerFeatures.SyncToolSpecification(GET_TASK_INFO_TOOL, this::handleGetTaskInfo),
                new McpServerFeatures.SyncToolSpecification(RESET_TASK_TOOL, this::handleResetTask),
                new McpServerFeatures.SyncToolSpecification(GET_EXEC_CONTEXT_INFO_TOOL, this::handleGetExecContextInfo),
                new McpServerFeatures.SyncToolSpecification(GET_EXEC_CONTEXT_GRAPH_TOOL, this::handleGetExecContextGraph),
                new McpServerFeatures.SyncToolSpecification(GET_EXEC_CONTEXT_TASK_STATE_TOOL, this::handleGetExecContextTaskState),
                new McpServerFeatures.SyncToolSpecification(GET_EXEC_CONTEXT_VARIABLE_STATE_TOOL, this::handleGetExecContextVariableState),
                new McpServerFeatures.SyncToolSpecification(LIST_SOURCE_CODES_TOOL, this::handleListSourceCodes),
                new McpServerFeatures.SyncToolSpecification(GET_SOURCE_CODE_TOOL, this::handleGetSourceCode),
                new McpServerFeatures.SyncToolSpecification(IMPORT_BUNDLE_FROM_GIT_TOOL, this::handleImportBundleFromGit),
                new McpServerFeatures.SyncToolSpecification(GET_META_STORAGE_RECORD_TOOL, this::handleGetMetaStorageRecord),
                new McpServerFeatures.SyncToolSpecification(SELECT_META_STORAGE_RECORD_TOOL, this::handleSelectMetaStorageRecord),
                new McpServerFeatures.SyncToolSpecification(DELETE_META_STORAGE_RECORD_TOOL, this::handleDeleteMetaStorageRecord),
                new McpServerFeatures.SyncToolSpecification(LIST_META_STORAGE_REC_KEYS_TOOL, this::handleListMetaStorageRecKeys)
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

    // ==================== Tool 16: execution gate status ====================

    private static final Tool EXECUTION_GATE_STATUS_TOOL = Tool.builder("mh_execution_gate_status",
                    objectSchema(Map.of(), List.of()))
            .title("Execution Gate Status")
            .description("What the Dispatcher is currently withholding from Processors and why - the same view as the "
                    + "execution-gate page in the UI. Two halves: active blocks (a quarantined Function code, API key "
                    + "or Processor, with the reason and how long is left), and the rejection reasons seen recently, "
                    + "each with exemplar Tasks. ❗ This is the tool that explains a Task sitting in NONE and never "
                    + "being assigned: 'interpreter_is_undefined' names an env no Processor declares - check "
                    + "mh_list_processors envCodes against the Function's env; 'functions_not_ready' means no "
                    + "Processor has reported holding the Function at the revision the Task is pinned to. "
                    + "bucketsPresent matters more than count: a reason firing across the whole window has stopped "
                    + "being transient whatever its volume.")
            .build();

    private CallToolResult handleExecutionGateStatus(McpSyncServerExchange exchange, CallToolRequest request) {
        log.info("01.260.400 MCP executionGateStatus()");
        final long now = System.currentTimeMillis();
        final ExecutionGateViewData.GateStatus status = new ExecutionGateViewData.GateStatus(
                executionGateService.liveRecords().stream()
                        .map(r -> new ExecutionGateViewData.GateRecordView(
                                r.scope(), r.refKey(), r.reasonCode(), r.blockedUntil(), r.blockedUntil() - now))
                        .toList(),
                gateMonitoring.actionableView(now).stream()
                        .map(level -> new ExecutionGateViewData.ReasonLevelView(
                                level.reason().name(),
                                level.rejectionClass().name(),
                                level.count(),
                                level.bucketsPresent(),
                                level.exemplars().stream()
                                        .map(e -> new ExecutionGateViewData.ExemplarView(
                                                e.atMills(), e.taskId(), e.functionCode(), e.processorId(), e.offendingValue()))
                                        .toList()))
                        .toList());
        return toCallToolResult(status);
    }

    // ==================== Tool 15: list processors ====================

    private static final Tool LIST_PROCESSORS_TOOL = Tool.builder("mh_list_processors",
                    objectSchema(Map.of(), List.of()))
            .title("List Processors")
            .description("List the Processors known to this Dispatcher, newest heartbeat first - the same view as the "
                    + "Processors page in the UI. Answers why a Task sits unassigned: whether any Processor is "
                    + "connected at all, when it was last seen, whether it is blacklisted and why, which envs it "
                    + "declares (an external Function whose 'env' is not among them can never be run there), and "
                    + "which of its cores are already busy. Carries every column of that page, with the raw status "
                    + "yaml replaced by the fields worth acting on - os, envCodes, taskParamsVersion, errors.")
            .build();

    private CallToolResult handleListProcessors(McpSyncServerExchange exchange, CallToolRequest request) {
        log.info("01.260.360 MCP listProcessors()");
        final ProcessorData.ProcessorsResult result = processorTopLevelService.getProcessors(Consts.PAGE_REQUEST_100_REC);
        final List<ProcessorDto> dtos = new ArrayList<>();
        for (ProcessorData.ProcessorStatus ps : result.items) {
            final ProcessorStatusYaml status = ps.processor.getProcessorStatusYaml();
            // envs is the field a caller actually needs here: a Function declaring env 'python' is only
            // runnable on a Processor whose env.yaml defines that code. The exec line itself is deliberately
            // NOT returned - it is a local command line, and the question this answers is which codes exist.
            final List<String> envCodes = status.env == null ? null : status.env.envs.keySet().stream().sorted().toList();
            final ProcessorQuotasDto quotas = status.env==null ? null : new ProcessorQuotasDto(
                    status.env.quotas.limit, status.env.quotas.defaultValue, status.env.quotas.disabled,
                    status.env.quotas.values.stream()
                            .map(q -> q.tag + "=" + q.amount + (q.disabled ? " (disabled)" : ""))
                            .toList());
            final List<ProcessorCoreDto> cores = ps.cores.stream()
                    .map(c -> new ProcessorCoreDto(c.id(), c.code(), c.busy()))
                    .toList();
            dtos.add(new ProcessorDto(
                    ps.processor.id, ps.processor.description, ps.ip, ps.host, ps.active, ps.functionProblem,
                    ps.blacklisted, ps.blacklistReason, ps.blacklistedForMills,
                    ps.lastSeen, status.taskParamsVersion,
                    status.os == null ? null : status.os.name(),
                    envCodes, quotas, status.currDir, status.errors, cores));
        }
        return toCallToolResult(dtos);
    }

    // ==================== Tool 14: create execContext ====================

    private static final Tool CREATE_EXEC_CONTEXT_TOOL = Tool.builder("mh_create_exec_context",
                    objectSchema(
                            Map.of(
                                    "sourceCodeId", Map.of("type", "integer",
                                            "description", "Numeric id of the SourceCode to instantiate. Use mh_list_source_codes to discover it."),
                                    "companyId", Map.of("type", "integer",
                                            "description", "Unique id of the company owning the SourceCode"),
                                    "accountId", Map.of("type", "integer",
                                            "description", "Optional account id recorded as the creator. Defaults to 0.")),
                            List.of("sourceCodeId", "companyId")))
            .title("Create ExecContext")
            .description("Create an ExecContext from a SourceCode and produce its Tasks - the same operation as the "
                    + "'create ExecContext' button in the UI (POST /exec-context-add-commit). The returned stateName "
                    + "reports the state it landed in; createExecContextAndStart already leaves it STARTED, so "
                    + "mh_exec_context_target_state is only needed for one that isn't. "
                    + "Fails when the SourceCode declares source-level input variables, because those must be "
                    + "initialized before Tasks can be produced.")
            .build();

    private CallToolResult handleCreateExecContext(McpSyncServerExchange exchange, CallToolRequest request) {
        final Map<String, Object> arguments = request.arguments();
        final Long sourceCodeId = getRequiredLong(arguments, "sourceCodeId");
        final UserContext userContext = userContextOf(exchange);
        final Long companyId = userContext.getCompanyId();

        log.info("01.260.320 MCP createExecContext(sourceCodeId={}, companyId={})", sourceCodeId, companyId);

        final ExecContextApiData.UserExecContext context =
                new ExecContextApiData.UserExecContext(userContext.getAccountId(), companyId);

        final ExecContextCreatorService.ExecContextCreationResult result =
                execContextCreatorTopLevelService.createExecContextAndStart(sourceCodeId, context, true, null,
                        new ExecContextData.ExecContextCreationInfo("by mh_create_exec_context"));

        final List<String> errors = result.getErrorMessagesAsList();
        final ExecContextImpl ec = result.execContext;
        if (ec == null) {
            return toCallToolResult(new CreateExecContextResultDto(false, null, sourceCodeId, companyId,
                    result.sourceCode == null ? null : result.sourceCode.uid, null, null,
                    errors.isEmpty() ? List.of("01.260.340 ExecContext wasn't created and no error was reported") : errors,
                    result.getInfoMessagesAsList()));
        }
        return toCallToolResult(new CreateExecContextResultDto(errors.isEmpty(), ec.id, sourceCodeId, companyId,
                result.sourceCode == null ? null : result.sourceCode.uid,
                ec.state, EnumsApi.ExecContextState.toState(ec.state).name(),
                errors, result.getInfoMessagesAsList()));
    }

    // ==================== Tool 3: set an ExecContext's target state ====================

    private static final Tool EXEC_CONTEXT_TARGET_STATE_TOOL = Tool.builder("mh_exec_context_target_state",
                    objectSchema(
                            Map.of(
                                    "execContextId", Map.of("type", "integer",
                                            "description", "Numeric id of the ExecContext"),
                                    "state", Map.of("type", "string", "enum", List.of("STARTED", "STOPPED"),
                                            "description", "Target state. STARTED admits its Tasks to Processors; STOPPED withholds them.")),
                            List.of("execContextId", "state")))
            .title("Set ExecContext Target State")
            .description("Move an ExecContext to a target state - the same operation as the start and stop buttons in "
                    + "the UI (GET /exec-context-target-state). Only STARTED and STOPPED are settable; the terminal "
                    + "states are outcomes the Dispatcher records, not states to assign. "
                    + "❗ The change CASCADES to every related ExecContext sharing this one's root, so stopping a "
                    + "parent stops the children it spawned through mh.exec-source-code. An ExecContext that is "
                    + "neither STARTED nor STOPPED - one already FINISHED, say - is skipped rather than dragged back.")
            .build();

    private CallToolResult handleExecContextTargetState(McpSyncServerExchange exchange, CallToolRequest request) {
        final Long execContextId = getRequiredLong(request.arguments(), "execContextId");
        final String state = getRequiredString(request.arguments(), "state");
        log.info("01.260.420 MCP execContextTargetState({}, {})", execContextId, state);

        // ❗ Deliberately the String-taking overload, which is what the UI button calls. The
        // ExecContextState-taking one changes ONE ExecContext: the two tools this replaced used it, so
        // stopping a parent left its children running. This one also rejects an unsettable state by name
        // rather than silently writing it.
        final OperationStatusRest status = execContextTopLevelService.changeExecContextState(
                state, execContextId, userContextOf(exchange));

        final boolean ok = status.status == EnumsApi.OperationStatus.OK;
        return toCallToolResult(new OperationResultDto(ok, ok
                ? "ExecContext #" + execContextId + " and every related ExecContext moved to " + state.toUpperCase()
                : String.join("; ", status.getErrorMessagesAsList())));
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

    // ==================== Tool 17: get one meta storage record by row id ====================

    private static final Tool GET_META_STORAGE_RECORD_TOOL = Tool.builder("mh_get_meta_storage_record",
                    objectSchema(
                            Map.of("id", Map.of("type", "integer",
                                            "description", "Numeric row id of the record - the ID column of whichever table 'synthetic' selects"),
                                    "synthetic", Map.of("type", "boolean",
                                            "description", "Which table to read: true -> MH_META_STORAGE_SYNTHETIC, false -> MH_META_STORAGE. Required, no default.")),
                            List.of("id", "synthetic")))
            .title("Get Meta Storage Record")
            .description("Get one meta storage record by its row id. 'synthetic' chooses the table and has no default: "
                    + "true reads MH_META_STORAGE_SYNTHETIC, false reads MH_META_STORAGE. The two tables carry identical "
                    + "columns and allocate ids from independent sequences, so the same id addresses a DIFFERENT row in "
                    + "each - a wrong flag returns another record rather than an error, which is why the returned object "
                    + "repeats the flag back. Returns the whole row: companyId, type, recKey, body, gen, version and "
                    + "updatedAt. \u2757 'body' is opaque - it is returned exactly as stored and was never parsed by MH, "
                    + "so its encoding is whatever the writer chose. The natural key of a record is (companyId, type, "
                    + "recKey); this tool addresses by row id, which is what a log line or a foreign reference carries.")
            .build();

    private CallToolResult handleGetMetaStorageRecord(McpSyncServerExchange exchange, CallToolRequest request) {
        final Map<String, Object> arguments = request.arguments();
        final Long id = getRequiredLong(arguments, "id");
        final boolean synthetic = getRequiredBoolean(arguments, "synthetic");
        log.info("01.260.440 MCP getMetaStorageRecord(id={}, synthetic={})", id, synthetic);

        // MetaStorage and MetaStorageSynthetic share every column but no supertype, so the branch maps
        // each on its own side and nothing after this line has to know which table was read.
        final Optional<MetaStorageRecordDto> dto = synthetic
                ? metaStorageSyntheticRepository.findById(id).map(MhMcpToolDefinitions::toDto)
                : metaStorageRepository.findById(id).map(MhMcpToolDefinitions::toDto);

        return dto.map(this::toCallToolResult)
                .orElseGet(() -> errorResult("Record #" + id + " not found in " + tableName(synthetic)));
    }

    private static String tableName(boolean synthetic) {
        return synthetic ? "MH_META_STORAGE_SYNTHETIC" : "MH_META_STORAGE";
    }

    private static MetaStorageRecordDto toDto(MetaStorage m) {
        return new MetaStorageRecordDto(false, m.id, m.version, m.companyId, m.type, m.recKey, m.gen, m.updatedAt, m.body);
    }

    private static MetaStorageRecordDto toDto(MetaStorageSynthetic m) {
        return new MetaStorageRecordDto(true, m.id, m.version, m.companyId, m.type, m.recKey, m.gen, m.updatedAt, m.body);
    }

    // ==================== Tool 18: select one record by its natural key ====================

    private static final Tool SELECT_META_STORAGE_RECORD_TOOL = Tool.builder("mh_select_meta_storage_record",
                    objectSchema(
                            Map.of("companyId", Map.of("type", "integer",
                                            "description", "Owning company id - the COMPANY_ID column, and the first segment of the natural key"),
                                    "type", Map.of("type", "string",
                                            "description", "Entity kind - the TYPE column. A column value, never an enum; opaque to MH."),
                                    "recKey", Map.of("type", "string",
                                            "description", "Natural key within (companyId, type) - the REC_KEY column. Opaque to MH."),
                                    "synthetic", Map.of("type", "boolean",
                                            "description", "Which table to address: true -> MH_META_STORAGE_SYNTHETIC, false -> MH_META_STORAGE. Required, no default.")),
                            List.of("companyId", "type", "recKey", "synthetic")))
            .title("Select Meta Storage Record")
            .description("Get one meta storage record addressed by its natural key (companyId, type, recKey) - the "
                    + "same row mh_get_meta_storage_record returns, reached the way a caller normally knows it. "
                    + "\u2757 Prefer this over the id form: (COMPANY_ID, TYPE, REC_KEY) is what the schema declares "
                    + "UNIQUE and what an upsert lands on, so it is stable, while a row id is an internal allocation "
                    + "from mh_gen_ids that nothing outside the table refers to. 'synthetic' chooses the table and has "
                    + "no default. Returns the whole row including the row id, which is what makes this the way to "
                    + "discover an id for the other tools. \u2757 'body' is opaque - returned exactly as stored, never "
                    + "parsed by MH.")
            .build();

    private CallToolResult handleSelectMetaStorageRecord(McpSyncServerExchange exchange, CallToolRequest request) {
        final Map<String, Object> arguments = request.arguments();
        final Long companyId = getRequiredLong(arguments, "companyId");
        final String type = getRequiredString(arguments, "type");
        final String recKey = getRequiredString(arguments, "recKey");
        final boolean synthetic = getRequiredBoolean(arguments, "synthetic");
        log.info("01.260.460 MCP selectMetaStorageRecord(companyId={}, type={}, recKey={}, synthetic={})",
                companyId, type, recKey, synthetic);

        final MetaStorageRecordDto dto;
        if (synthetic) {
            final MetaStorageSynthetic row = metaStorageSyntheticRepository.findByNaturalKey(companyId, type, recKey);
            dto = row==null ? null : toDto(row);
        }
        else {
            final MetaStorage row = metaStorageRepository.findByNaturalKey(companyId, type, recKey);
            dto = row==null ? null : toDto(row);
        }
        if (dto==null) {
            return errorResult("No record for (companyId=" + companyId + ", type=" + type + ", recKey=" + recKey
                    + ") in " + tableName(synthetic));
        }
        return toCallToolResult(dto);
    }

    // ==================== Tool 19: delete one record by its natural key ====================

    private static final Tool DELETE_META_STORAGE_RECORD_TOOL = Tool.builder("mh_delete_meta_storage_record",
                    objectSchema(
                            Map.of("companyId", Map.of("type", "integer",
                                            "description", "Owning company id - the COMPANY_ID column, and the first segment of the natural key"),
                                    "type", Map.of("type", "string",
                                            "description", "Entity kind - the TYPE column. A column value, never an enum; opaque to MH."),
                                    "recKey", Map.of("type", "string",
                                            "description", "Natural key within (companyId, type) - the REC_KEY column. Opaque to MH."),
                                    "synthetic", Map.of("type", "boolean",
                                            "description", "Which table to address: true -> MH_META_STORAGE_SYNTHETIC, false -> MH_META_STORAGE. Required, no default.")),
                            List.of("companyId", "type", "recKey", "synthetic")))
            .title("Delete Meta Storage Record")
            .description("Delete the one meta storage record addressed by its natural key (companyId, type, recKey). "
                    + "\u2757 This DESTROYS data and there is no undo - MH keeps no history of a meta storage row, so a "
                    + "deleted body is gone. Deleting by natural key rather than by row id is deliberate: the key is "
                    + "what the caller reasoned about, whereas a stale row id could address a different record. "
                    + "'synthetic' chooses the table and has no default. A key matching nothing is reported as "
                    + "ok=false and changes nothing, so a repeated delete is safe. Goes through the same transactional "
                    + "path a pipeline write uses.")
            .build();

    private CallToolResult handleDeleteMetaStorageRecord(McpSyncServerExchange exchange, CallToolRequest request) {
        final Map<String, Object> arguments = request.arguments();
        final Long companyId = getRequiredLong(arguments, "companyId");
        final String type = getRequiredString(arguments, "type");
        final String recKey = getRequiredString(arguments, "recKey");
        final boolean synthetic = getRequiredBoolean(arguments, "synthetic");
        log.info("01.260.480 MCP deleteMetaStorageRecord(companyId={}, type={}, recKey={}, synthetic={})",
                companyId, type, recKey, synthetic);

        // \u2757 Through the orchestrator, never the repository: it resolves the key to a row id OUTSIDE the
        // transaction and hands only the id to the tx service, which is the rule every other write here follows.
        final int deleted = synthetic
                ? metaStorageSyntheticService.deleteByNaturalKey(companyId, type, recKey)
                : metaStorageService.deleteByNaturalKey(companyId, type, recKey);

        final String key = "(companyId=" + companyId + ", type=" + type + ", recKey=" + recKey + ") in " + tableName(synthetic);
        return toCallToolResult(new OperationResultDto(deleted > 0, deleted > 0
                ? "Deleted the record " + key
                : "No record for " + key + " - nothing was deleted"));
    }

    // ==================== Tool 20: every recKey of one (companyId, type) ====================

    private static final Tool LIST_META_STORAGE_REC_KEYS_TOOL = Tool.builder("mh_list_meta_storage_rec_keys",
                    objectSchema(
                            Map.of("companyId", Map.of("type", "integer",
                                            "description", "Owning company id - the COMPANY_ID column"),
                                    "type", Map.of("type", "string",
                                            "description", "Entity kind - the TYPE column. A column value, never an enum; opaque to MH."),
                                    "synthetic", Map.of("type", "boolean",
                                            "description", "Which table to read: true -> MH_META_STORAGE_SYNTHETIC, false -> MH_META_STORAGE. Required, no default.")),
                            List.of("companyId", "type", "synthetic")))
            .title("List Meta Storage Rec Keys")
            .description("List every recKey stored under one (companyId, type), ordered by recKey so a run is "
                    + "reproducible. \u2757 Bodies are NOT read - this is the selection step, and the payload for a "
                    + "chosen key is fetched afterwards with mh_select_meta_storage_record. Use it to discover what a "
                    + "type actually holds before addressing anything: the store enumerates itself, so no registry "
                    + "lists these keys anywhere else. A (companyId, type) that has never been written is an empty "
                    + "list with count 0, not an error - a type only exists by virtue of something having been "
                    + "written under it.")
            .build();

    private CallToolResult handleListMetaStorageRecKeys(McpSyncServerExchange exchange, CallToolRequest request) {
        final Map<String, Object> arguments = request.arguments();
        final Long companyId = getRequiredLong(arguments, "companyId");
        final String type = getRequiredString(arguments, "type");
        final boolean synthetic = getRequiredBoolean(arguments, "synthetic");
        log.info("01.260.500 MCP listMetaStorageRecKeys(companyId={}, type={}, synthetic={})", companyId, type, synthetic);

        final List<String> recKeys = synthetic
                ? metaStorageSyntheticService.listKeys(companyId, type)
                : metaStorageService.listKeys(companyId, type);

        return toCallToolResult(new MetaStorageRecKeysDto(synthetic, companyId, type, recKeys.size(), recKeys));
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

    /**
     * \u2757 Deliberately NOT {@code Boolean.parseBoolean}, which answers false to everything that is not
     * the word "true" - a typo would silently become "read the non-synthetic table" and the caller
     * would get a real record from the wrong place. An unrecognized value is rejected by name instead.
     */
    private static boolean getRequiredBoolean(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Required parameter '" + key + "' is missing");
        }
        if (value instanceof Boolean b) {
            return b;
        }
        final String s = value.toString().strip();
        if ("true".equalsIgnoreCase(s)) {
            return true;
        }
        if ("false".equalsIgnoreCase(s)) {
            return false;
        }
        throw new IllegalArgumentException("Parameter '" + key + "' must be a boolean, was: " + value);
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
