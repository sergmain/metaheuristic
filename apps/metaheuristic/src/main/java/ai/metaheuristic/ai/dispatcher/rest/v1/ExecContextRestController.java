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

package ai.metaheuristic.ai.dispatcher.rest.v1;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTxService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStateDownloadService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.commons.account.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>Error code prefix: {@code 01.571.} (unique to this class).
 *
 * @author Serge
 * Date: 7/4/2019
 * Time: 3:55 PM
 */

// all urls in "/rest/v1/dispatcher/source-code" because of angular.
// need to change an angular code too. but rn will like that
@RequestMapping("/rest/v1/dispatcher/source-code")
@RestController
@Profile("dispatcher")
@CrossOrigin
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextRestController {

    private final ExecContextTopLevelService execContextTopLevelService;
    private final ExecContextTxService execContextTxService;
    private final ExecContextCreatorTopLevelService execContextCreatorTopLevelService;
    private final UserContextService userContextService;
    private final ExecContextStateDownloadService execContextStateDownloadService;
    private final ExecContextRepository execContextRepository;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleExecContextAddingResult {
        public Long execContextId;
    }

    @GetMapping("/exec-contexts/{sourceCodeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER')")
    public ExecContextApiData.ExecContextsResult execContexts(@PathVariable Long sourceCodeId,
                                                              @PageableDefault(size = 5) Pageable pageable, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        return execContextTxService.getExecContextsOrderByCreatedOnDesc(sourceCodeId, pageable, context);
    }

    /**
     * !! right now the reference to global variable isn't supported.
     * all global variables must be specified in SourceCode.
     *
     * create ExecContext by uid of sourceCode
     * useful for creating ExecContext from command-line with cURL
     *
     * @param uid Uid of sourceCode
     * @param variable
     *
     * @return
     */
    @PostMapping("/uid-exec-context-add-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public SimpleExecContextAddingResult execContextAddCommit(String uid, @SuppressWarnings("unused") @Nullable String variable, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        ExecContextCreatorService.ExecContextCreationResult execContextResult =
            execContextCreatorTopLevelService.createExecContextAndStart(uid,
                new ExecContextApiData.UserExecContext(context.getAccountId(), context.getCompanyId()), new ExecContextData.ExecContextCreationInfo("by /uid-exec-context-add-commit"));
        return new SimpleExecContextAddingResult(execContextResult.execContext.getId());
    }

    /**
     * right now reference to global variable isn't supported. all global variables must be specified in SourceCode.
     */
    @PostMapping("/exec-context-add-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public SourceCodeApiData.ExecContextResult execContextAddCommit(Long sourceCodeId, @SuppressWarnings("unused") @Nullable String variable, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        ExecContextApiData.UserExecContext context1 = new ExecContextApiData.UserExecContext(context.getAccountId(), context.getCompanyId());
        ExecContextCreatorService.ExecContextCreationResult execContextResult = execContextCreatorTopLevelService.createExecContextAndStart(
            sourceCodeId, context1, true, null, new ExecContextData.ExecContextCreationInfo("by /exec-context-add-commit"));

        SourceCodeApiData.ExecContextResult result = new SourceCodeApiData.ExecContextResult(
            execContextResult.sourceCode, execContextResult.execContext, execContextResult.getInfoMessages(), execContextResult.getErrorMessages());
        return result;
    }

    @GetMapping(value = "/exec-context/{sourceCodeId}/{execContextId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public SourceCodeApiData.ExecContextResult execContextEdit(@SuppressWarnings("unused") @PathVariable Long sourceCodeId, @PathVariable Long execContextId) {
        return execContextTopLevelService.getExecContextExtended(execContextId);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'OPERATOR', 'MANAGER')")
    @PostMapping("/exec-context-delete-commit")
    public OperationStatusRest execContextDeleteCommit(Long sourceCodeId, Long execContextId, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        return execContextTopLevelService.deleteExecContextById(execContextId, context);
    }

    @PostMapping("/exec-context/task-reset-cache")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public OperationStatusRest resetCache(Long taskId, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        return execContextTopLevelService.resetCache(taskId, context);
    }

    @GetMapping("/exec-context-target-state/{sourceCodeId}/{state}/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public OperationStatusRest execContextTargetState(
            @SuppressWarnings("unused") @PathVariable Long sourceCodeId, @PathVariable String state,
            @PathVariable Long id, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        return execContextTopLevelService.changeExecContextState(state, id, context);
    }

    @GetMapping("/exec-context-state/{sourceCodeId}/{execContextId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER', 'OPERATOR')")
    public ExecContextApiData.ExecContextStateResult execContextsState(@PathVariable Long sourceCodeId, @PathVariable Long execContextId, @RequestParam(required = false) @Nullable String versions, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        ExecContextApiData.ExecContextStateResult execContextState = execContextTopLevelService.getExecContextState(sourceCodeId, execContextId, versions, authentication);
        return execContextState;
    }

    @GetMapping("/exec-context-simple-state/{execContextId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER', 'OPERATOR')")
    public ExecContextApiData.ExecContextSimpleStateResult getExecContextSimpleState(@PathVariable Long execContextId, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        ExecContextApiData.ExecContextSimpleStateResult result = execContextTopLevelService.getExecContextSimpleState(execContextId, context);
        return result;
    }

    @GetMapping("/exec-context-task-exec-info/{sourceCodeId}/{execContextId}/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER', 'OPERATOR')")
    public ExecContextApiData.TaskExecInfo taskExecInfo(@PathVariable Long sourceCodeId, @PathVariable Long execContextId, @PathVariable Long taskId, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        ExecContextApiData.TaskExecInfo execContextState = execContextTopLevelService.getTaskExecInfo(sourceCodeId, execContextId, taskId);
        return execContextState;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'OPERATOR', 'MANAGER')")
    @GetMapping(value= "/exec-context/{execContextId}/download-variable/{variableId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public HttpEntity<AbstractResource> downloadVariable(
            HttpServletRequest request, @PathVariable("execContextId") Long execContextId, @PathVariable("variableId") Long variableId,
            Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        try {
            CleanerInfo resource = execContextTxService.downloadVariable(execContextId, variableId, context.getCompanyId());
            if (resource==null) {
                return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
            }
            request.setAttribute(Consts.RESOURCES_TO_CLEAN, resource.toClean);
            return resource.entity == null ? new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE) : resource.entity;
        } catch (CommonErrorWithDataException e) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER', 'OPERATOR')")
    @GetMapping(value= "/exec-context/{execContextId}/download-states", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public HttpEntity<AbstractResource> downloadExecContextStates(
            HttpServletRequest request, @PathVariable("execContextId") Long execContextId,
            Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        CleanerInfo resource = execContextStateDownloadService.downloadExecContextStates(execContextId);
        if (resource.entity == null) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        }
        request.setAttribute(Consts.RESOURCES_TO_CLEAN, resource.toClean);
        return resource.entity;
    }

    @PostMapping("/exec-context-descs")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER')")
    public ExecContextData.ExecContextDescsResult execContextDescs(@RequestBody ExecContextData.@Nullable ExecContextDescsRequest request) {
        ExecContextData.ExecContextDescsResult result = execContextTopLevelService.execContextDescs(request);
        return result;
    }

    /**
     * Creates an ExecContext from a SourceCode that declares source-level input variables, initializes those
     * variables from the body, and produces its Tasks - for a REUSABLE workflow, one that takes its subject at run
     * time instead of having it written into the .mhsc. {@code /exec-context-add-commit} cannot launch such a
     * SourceCode: it has no way to carry the values, and production refuses an uninitialized input (562.120).
     *
     * <pre>
     * curl -u login:password -H "Content-Type: application/json" \
     *   -d '{"sourceCodeId": 256, "variables": {"formUrl": "https://example.org/form", "production": null}}' \
     *   http://localhost:8080/rest/v1/dispatcher/source-code/exec-context-with-variables-add-commit
     * </pre>
     *
     * <p>The values are applied by the same service path as the {@code mh_create_exec_context_with_variables} MCP
     * tool, so both refuse alike: a declared input with no value (562.120), a name the SourceCode doesn't declare
     * (562.122), a null for an input not declared nullable (01.562.124). Each refusal comes back in
     * {@code errorMessages} with {@code execContextId} null, and nothing is left behind.
     */
    @PostMapping(value = "/exec-context-with-variables-add-commit", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public ExecContextData.ExecContextWithVariablesResult execContextWithVariablesAddCommit(
            @RequestBody ExecContextData.ExecContextWithVariablesRequest request, Authentication authentication) {
        final Long sourceCodeId = request.sourceCodeId;
        if (sourceCodeId==null) {
            final ExecContextData.ExecContextWithVariablesResult result = new ExecContextData.ExecContextWithVariablesResult();
            result.addErrorMessage("01.571.020 'sourceCodeId' is required: the body is "
                + "{\"sourceCodeId\": <id>, \"variables\": {\"<name>\": \"<value>\" | null, ...}}");
            return result;
        }
        UserContext context = userContextService.getContext(authentication);
        final ExecContextCreatorService.ExecContextCreationResult creation = execContextCreatorTopLevelService.createExecContextAndStart(
            sourceCodeId, new ExecContextApiData.UserExecContext(context.getAccountId(), context.getCompanyId()), true, null,
            new ExecContextData.ExecContextCreationInfo("by /exec-context-with-variables-add-commit"),
            toInputVariables(request.variables));
        return toResult(sourceCodeId, creation);
    }

    /**
     * The body's name -&gt; value map as the creator takes it. A null map is no values; a null value is an explicit
     * "no value" ({@code VariableValue(null)}), not an absent one - so the map keeps the name.
     */
    public static Map<String, ExecContextData.VariableValue> toInputVariables(@Nullable Map<String, @Nullable String> variables) {
        final Map<String, ExecContextData.VariableValue> values = new HashMap<>();
        if (variables!=null) {
            variables.forEach((name, value) -> values.put(name, new ExecContextData.VariableValue(value)));
        }
        return values;
    }

    /**
     * The answer for one creation: the ExecContext's id and state when it exists, the creator's messages always, and
     * an error of its own when there is neither an ExecContext nor an error saying why.
     */
    public static ExecContextData.ExecContextWithVariablesResult toResult(
            Long sourceCodeId, ExecContextCreatorService.ExecContextCreationResult creation) {
        final ExecContextData.ExecContextWithVariablesResult result = new ExecContextData.ExecContextWithVariablesResult();
        result.sourceCodeId = sourceCodeId;
        result.sourceCodeUid = creation.sourceCode==null ? null : creation.sourceCode.uid;
        if (!creation.getErrorMessagesAsList().isEmpty()) {
            result.addErrorMessages(creation.getErrorMessagesAsList());
        }
        if (!creation.getInfoMessagesAsList().isEmpty()) {
            result.addInfoMessages(creation.getInfoMessagesAsList());
        }
        final ExecContextImpl ec = creation.execContext;
        if (ec==null) {
            if (!result.isErrorMessages()) {
                result.addErrorMessage("01.571.040 ExecContext wasn't created and no error was reported");
            }
            return result;
        }
        result.execContextId = ec.id;
        result.state = ec.state;
        result.stateName = EnumsApi.ExecContextState.toState(ec.state).name();
        return result;
    }
}
