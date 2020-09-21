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
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSelectorService;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
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

/**
 * @author Serge
 * Date: 9/3/2020
 * Time: 1:22 PM
 */
@SuppressWarnings("DuplicatedCode")
@RestController
@RequestMapping("/rest/v1/dispatcher/company/batch")
@Slf4j
@Profile("dispatcher")
@CrossOrigin
@RequiredArgsConstructor
public class BatchForOperatorRestController {

    private final BatchTopLevelService batchTopLevelService;
    private final UserContextService userContextService;
    private final SourceCodeSelectorService sourceCodeSelectorService;

    @GetMapping("/company-batches/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MASTER_OPERATOR', 'MASTER_SUPPORT')")
    public BatchData.BatchesResult batches(@PageableDefault(size = 20) Pageable pageable, @PathVariable Long companyUniqueId) {
        BatchData.BatchesResult batchesResult = batchTopLevelService.getBatches(pageable, companyUniqueId, null, true, false);
        return batchesResult;
    }

    @GetMapping("/company-batch-delete/{companyUniqueId}/{batchId}")
    @PreAuthorize("hasAnyRole('MASTER_OPERATOR')")
    public BatchData.Status processBatchDelete(@PathVariable Long companyUniqueId, @PathVariable Long batchId) {
        BatchData.Status status = batchTopLevelService.getBatchProcessingStatus(batchId, companyUniqueId, true);
        return status;
    }

    @PostMapping("/company-batch-delete-commit/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MASTER_OPERATOR')")
    public OperationStatusRest processBatchDeleteCommit(Long batchId, @PathVariable Long companyUniqueId) {
        OperationStatusRest r = batchTopLevelService.processBatchDeleteCommit(batchId, companyUniqueId, false);
        return r;
    }

    /**
     *
     * @param batchIds comma-separated list of batchId for deleting
     * @param companyUniqueId Company.uniqueId
     * @return
     */
    @PostMapping("/company-batch-bulk-delete-commit/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MASTER_OPERATOR')")
    public BatchData.BulkOperations processBatchesBulkDeleteCommit(String batchIds, @PathVariable Long companyUniqueId) {
        BatchData.BulkOperations r = batchTopLevelService.processBatchBulkDeleteCommit(batchIds, companyUniqueId, false);
        return r;
    }

    @GetMapping(value = "/company-batch-source-codes/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MASTER_OPERATOR')")
    public SourceCodeData.SourceCodesForCompany sourceCodesForCompany(@PathVariable Long companyUniqueId) {
        SourceCodeData.SourceCodesForCompany sourceCodes = sourceCodeSelectorService.getAvailableSourceCodesForCompany(companyUniqueId);
        return sourceCodes;
    }

    @PostMapping(value = "/company-batch-upload-from-file/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MASTER_OPERATOR')")
    public BatchData.UploadingStatus uploadFile(final MultipartFile file, @PathVariable Long companyUniqueId,
                                                Long sourceCodeId, Authentication authentication) {
        // create context with putting current user to specific company
        DispatcherContext context = userContextService.getContext(authentication, companyUniqueId);
        BatchData.UploadingStatus uploadingStatus = batchTopLevelService.batchUploadFromFile(file, sourceCodeId, context);
        return uploadingStatus;
    }

    @GetMapping(value= "/company-batch-status/{companyUniqueId}/{batchId}" )
    @PreAuthorize("hasAnyRole('MASTER_OPERATOR', 'MASTER_SUPPORT')")
    public BatchData.Status getBatchStatus(@PathVariable Long companyUniqueId, @PathVariable("batchId") Long batchId) {
        BatchData.Status status = batchTopLevelService.getBatchProcessingStatus(batchId, companyUniqueId, true);
        return status;
    }

    @GetMapping(value= "/company-batch-download-result/{companyUniqueId}/{batchId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('MASTER_OPERATOR', 'MASTER_SUPPORT')")
    public HttpEntity<AbstractResource> downloadProcessingResult(
            HttpServletRequest request,
            @PathVariable Long companyUniqueId, @PathVariable("batchId") Long batchId) throws IOException {
        final ResponseEntity<AbstractResource> entity;
        try {
            CleanerInfo resource = batchTopLevelService.getBatchProcessingResult(batchId, companyUniqueId, true);
            if (resource==null) {
                return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
            }
            entity = resource.entity;
            request.setAttribute(Consts.RESOURCES_TO_CLEAN, resource.toClean);
        } catch (CommonErrorWithDataException e) {
            // TODO 2019-10-13 in case of this exception, resources won't be cleaned. Need to re-write
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        }
        return entity;
    }

    @SuppressWarnings("TryWithIdenticalCatches")
    @GetMapping(value= "/company-batch-download-origin-file/{companyUniqueId}/{batchId}/{fileName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('MASTER_OPERATOR', 'MASTER_SUPPORT')")
    public HttpEntity<AbstractResource> downloadOriginFile(
            HttpServletRequest request,
            @PathVariable Long companyUniqueId,
            @PathVariable("batchId") Long batchId,
            @SuppressWarnings("unused") @PathVariable("fileName") String fileName) {
        final ResponseEntity<AbstractResource> entity;
        try {
            CleanerInfo resource = batchTopLevelService.getBatchOriginFile(batchId);
            if (resource==null) {
                return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
            }
            entity = resource.entity;
            request.setAttribute(Consts.RESOURCES_TO_CLEAN, resource.toClean);
        } catch (CommonErrorWithDataException e) {
            // TODO 2019-10-13 in case of this exception, resources won't be cleaned. Need to re-write
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        } catch (Throwable e) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        }
        return entity;
    }

}
