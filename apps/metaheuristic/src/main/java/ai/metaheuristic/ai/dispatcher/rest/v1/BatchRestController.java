/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.data.BatchData;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSelectorService;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * @author Serge
 * Date: 6/14/2019
 * Time: 1:23 AM
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/batch")
@Slf4j
@Profile("dispatcher")
@CrossOrigin
@RequiredArgsConstructor
public class BatchRestController {

    private final BatchTopLevelService batchTopLevelService;
    private final UserContextService userContextService;
    private final SourceCodeSelectorService sourceCodeSelectorService;
    private final DispatcherParamsService dispatcherParamsService;

    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'MANAGER')")
    @GetMapping("/batches")
    public BatchData.BatchesResult batches(
            @RequestParam(required = false, defaultValue = "false") boolean filterBatches,
            @PageableDefault(size = 20) Pageable pageable, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return batchTopLevelService.getBatches(pageable, context, false, filterBatches);
    }

    @GetMapping("/batch-exec-statuses")
    @PreAuthorize("isAuthenticated()")
    public BatchData.ExecStatuses batchExecStatuses(Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return batchTopLevelService.getBatchExecStatuses(context);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'MANAGER')")
    @PostMapping("/batches-part")
    public BatchData.BatchesResult batchesPart(
            @RequestParam(required = false, defaultValue = "false") boolean filterBatches,
            @PageableDefault(size = 20) Pageable pageable, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return batchTopLevelService.getBatches(pageable, context, false, filterBatches);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'MANAGER')")
    @GetMapping(value = "/batch-add")
    public SourceCodeData.SourceCodeUidsForCompany batchAdd(Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeData.SourceCodeUidsForCompany codes = new SourceCodeData.SourceCodeUidsForCompany();
        List<String> uids = dispatcherParamsService.getBatches();
        codes.items = sourceCodeSelectorService.filterSourceCodes(context, uids);
        return codes;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'MANAGER')")
    @GetMapping("/batch-delete/{batchId}")
    public BatchData.Status processResourceDelete(@PathVariable Long batchId, Authentication authentication) {
        return batchTopLevelService.getBatchProcessingStatus(batchId, userContextService.getContext(authentication).getCompanyId(), false);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'MANAGER')")
    @PostMapping("/batch-delete-commit")
    public OperationStatusRest processResourceDeleteCommit(Long batchId, Authentication authentication) {
        return batchTopLevelService.processBatchDeleteCommit(batchId, userContextService.getContext(authentication), true);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'MANAGER')")
    @PostMapping(value = "/batch-upload-from-file")
    public OperationStatusRest uploadFile(final MultipartFile file, Long sourceCodeId, Authentication authentication) {
        BatchData.UploadingStatus uploadingStatus = batchTopLevelService.batchUploadFromFile(file, sourceCodeId, userContextService.getContext(authentication));
        if (uploadingStatus.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, Objects.requireNonNull(uploadingStatus.getErrorMessages()));
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'MANAGER')")
    @GetMapping(value= "/batch-status/{batchId}" )
    public BatchData.Status getProcessingResourceStatus(@PathVariable("batchId") Long batchId, Authentication authentication) {
        return batchTopLevelService.getBatchProcessingStatus(batchId, userContextService.getContext(authentication).getCompanyId(), false);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'MANAGER')")
    @GetMapping(value= "/batch-download-result/{batchId}/{fileName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public HttpEntity<AbstractResource> downloadProcessingResult(
            HttpServletRequest request, @PathVariable("batchId") Long batchId,
            @SuppressWarnings("unused") @PathVariable("fileName") String fileName, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        try {
            CleanerInfo resource = batchTopLevelService.getBatchProcessingResultWitTx(batchId, context.getCompanyId(), false);
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
