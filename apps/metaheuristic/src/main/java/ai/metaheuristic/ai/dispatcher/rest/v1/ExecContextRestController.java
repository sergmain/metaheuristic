/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTxService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
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
        DispatcherContext context = userContextService.getContext(authentication);
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
        DispatcherContext context = userContextService.getContext(authentication);
        ExecContextCreatorService.ExecContextCreationResult execContextResult = execContextCreatorTopLevelService.createExecContextAndStart(uid, context.asUserExecContext());
        return new SimpleExecContextAddingResult(execContextResult.execContext.getId());
    }

    /**
     * right now reference to global variable isn't supported. all global variables must be specified in SourceCode.
     */
    @PostMapping("/exec-context-add-commit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public SourceCodeApiData.ExecContextResult execContextAddCommit(Long sourceCodeId, @SuppressWarnings("unused") @Nullable String variable, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        ExecContextCreatorService.ExecContextCreationResult execContextResult = execContextCreatorTopLevelService.createExecContextAndStart(sourceCodeId, context.asUserExecContext(), true);

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
        DispatcherContext context = userContextService.getContext(authentication);
        return execContextTopLevelService.deleteExecContextById(execContextId, context);
    }

    @PostMapping("/exec-context/task-reset-cache")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public OperationStatusRest resetCache(Long taskId, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return execContextTopLevelService.resetCache(taskId, context);
    }

    @GetMapping("/exec-context-target-state/{sourceCodeId}/{state}/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
    public OperationStatusRest execContextTargetState(
            @SuppressWarnings("unused") @PathVariable Long sourceCodeId, @PathVariable String state,
            @PathVariable Long id, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return execContextTopLevelService.changeExecContextState(state, id, context);
    }

    @GetMapping("/exec-context-state/{sourceCodeId}/{execContextId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER', 'OPERATOR')")
    public ExecContextApiData.ExecContextStateResult execContextsState(@PathVariable Long sourceCodeId, @PathVariable Long execContextId, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        ExecContextApiData.ExecContextStateResult execContextState = execContextTopLevelService.getExecContextState(sourceCodeId, execContextId, authentication);
        return execContextState;
    }

    @GetMapping("/exec-context-simple-state/{execContextId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER', 'OPERATOR')")
    public ExecContextApiData.ExecContextSimpleStateResult getExecContextSimpleState(@PathVariable Long execContextId, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        ExecContextApiData.ExecContextSimpleStateResult result = execContextTopLevelService.getExecContextSimpleState(execContextId, context);
        return result;
    }

    @GetMapping("/exec-context-task-exec-info/{sourceCodeId}/{execContextId}/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'MANAGER', 'OPERATOR')")
    public ExecContextApiData.TaskExecInfo taskExecInfo(@PathVariable Long sourceCodeId, @PathVariable Long execContextId, @PathVariable Long taskId, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        ExecContextApiData.TaskExecInfo execContextState = execContextTopLevelService.getTaskExecInfo(sourceCodeId, execContextId, taskId);
        return execContextState;
    }


    @PreAuthorize("hasAnyRole('ADMIN', 'DATA', 'OPERATOR', 'MANAGER')")
    @GetMapping(value= "/exec-context/{execContextId}/download-variable/{variableId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public HttpEntity<AbstractResource> downloadVariable(
            HttpServletRequest request, @PathVariable("execContextId") Long execContextId, @PathVariable("variableId") Long variableId,
            Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
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


}
