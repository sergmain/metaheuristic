/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.company;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.data.BatchData;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSelectorService;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.utils.ControllerUtils;
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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;

@SuppressWarnings("DuplicatedCode")
@Controller
@RequestMapping("/dispatcher/company/batch")
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class BatchForOperatorController {

    private final BatchTopLevelService batchTopLevelService;
    private final UserContextService userContextService;
    private final SourceCodeSelectorService sourceCodeSelectorService;

    @GetMapping("/company-batches/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MAIN_OPERATOR', 'MAIN_SUPPORT')")
    public String batches(
            Model model,
            @PageableDefault(size = 20) Pageable pageable,
            @ModelAttribute("errorMessage") final String errorMessage,
            @ModelAttribute("infoMessages") final String infoMessages,
            @PathVariable Long companyUniqueId
            ) {
        BatchData.BatchesResult batchesResult = batchTopLevelService.getBatches(pageable, companyUniqueId, null, true, false);
        ControllerUtils.addMessagesToModel(model, batchesResult);
        model.addAttribute("result", batchesResult);
        model.addAttribute("companyUniqueId", companyUniqueId);
        return "dispatcher/company/batch/company-batches";
    }

    @PostMapping("/company-batches-part/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MAIN_OPERATOR', 'MAIN_SUPPORT')")
    public String batchesPart(Model model, @PageableDefault(size = 20) Pageable pageable, @PathVariable Long companyUniqueId) {
        BatchData.BatchesResult batchesResult = batchTopLevelService.getBatches(pageable, companyUniqueId, null, true, false);
        ControllerUtils.addMessagesToModel(model, batchesResult);
        model.addAttribute("result", batchesResult);
        model.addAttribute("companyUniqueId", companyUniqueId);
        return "dispatcher/company/batch/company-batches :: table";
    }

    @GetMapping(value = "/company-batch-add/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MAIN_OPERATOR')")
    public String batchAdd(Model model, @PathVariable Long companyUniqueId) {
        SourceCodeData.SourceCodesForCompany sourceCodes = sourceCodeSelectorService.getAvailableSourceCodesForCompany(companyUniqueId);
        ControllerUtils.addMessagesToModel(model, sourceCodes);
        model.addAttribute("result", sourceCodes);
        model.addAttribute("companyUniqueId", companyUniqueId);
        return "dispatcher/company/batch/company-batch-add";
    }

    @GetMapping("/company-batch-delete/{companyUniqueId}/{batchId}")
    @PreAuthorize("hasAnyRole('MAIN_OPERATOR')")
    public String processResourceDelete(
            Model model,
            @PathVariable Long companyUniqueId,
            @PathVariable Long batchId, final RedirectAttributes redirectAttributes) {
        BatchData.Status status = batchTopLevelService.getBatchProcessingStatus(batchId, companyUniqueId, true);
        if (status.isErrorMessages()) {
            redirectAttributes.addAttribute("errorMessage", status.getErrorMessages());
            return "redirect:/dispatcher/company/batch/company-batches/" + companyUniqueId;
        }
        model.addAttribute("batchId", batchId);
        model.addAttribute("console", status.console);
        model.addAttribute("isOk", status.ok);
        model.addAttribute("companyUniqueId", companyUniqueId);
        return "dispatcher/company/batch/company-batch-delete";
    }

    @PostMapping("/company-batch-delete-commit/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MAIN_OPERATOR')")
    public String processResourceDeleteCommit(
            Long batchId,
            @PathVariable Long companyUniqueId,
            final RedirectAttributes redirectAttributes) {
        OperationStatusRest r = batchTopLevelService.processBatchDeleteCommit(batchId, companyUniqueId, false);
        if (r.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", r.getErrorMessagesAsList());
        }
        return "redirect:/dispatcher/company/batch/company-batches/" + companyUniqueId;
    }

    @PostMapping(value = "/company-batch-upload-from-file/{companyUniqueId}")
    @PreAuthorize("hasAnyRole('MAIN_OPERATOR')")
    public String uploadFile(
            final MultipartFile file,
            @PathVariable Long companyUniqueId,
            Long sourceCodeId, final RedirectAttributes redirectAttributes, Authentication authentication) {
        // create context with putting a current user to specific company
        // so a master operator can pretend to be a user of specific company
        DispatcherContext context = userContextService.getContext(authentication, companyUniqueId);
        BatchData.UploadingStatus uploadingStatus = batchTopLevelService.batchUploadFromFile(file, sourceCodeId, context);
        if (uploadingStatus.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", uploadingStatus.getErrorMessagesAsList());
        }
        return "redirect:/dispatcher/company/batch/company-batches/" + companyUniqueId;
    }

    @GetMapping(value= "/company-batch-status/{companyUniqueId}/{batchId}" )
    @PreAuthorize("hasAnyRole('MAIN_OPERATOR', 'MAIN_SUPPORT')")
    public String getProcessingResourceStatus(
            Model model,
            @PathVariable Long companyUniqueId,
            @PathVariable("batchId") Long batchId, final RedirectAttributes redirectAttributes) {
        BatchData.Status status = batchTopLevelService.getBatchProcessingStatus(batchId, companyUniqueId, true);
        if (status.isErrorMessages()) {
            redirectAttributes.addAttribute("errorMessage", status.getErrorMessages());
            return "redirect:/dispatcher/company/batch/company-batches/" + companyUniqueId;
        }
        model.addAttribute("batchId", batchId);
        model.addAttribute("console", status.console);
        model.addAttribute("companyUniqueId", companyUniqueId);
        return "dispatcher/company/batch/company-batch-status";
    }

    @GetMapping(value= "/company-batch-download-result/{companyUniqueId}/{batchId}/{fileName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('MAIN_OPERATOR', 'MAIN_SUPPORT')")
    public HttpEntity<AbstractResource> downloadProcessingResult(
            HttpServletRequest request,
            @PathVariable Long companyUniqueId,
            @PathVariable("batchId") Long batchId,
            @SuppressWarnings("unused") @PathVariable("fileName") String fileName) {
        try {
            CleanerInfo resource = batchTopLevelService.getBatchProcessingResultWitTx(batchId, companyUniqueId, true);
            if (resource==null) {
                return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
            }
            request.setAttribute(Consts.RESOURCES_TO_CLEAN, resource.toClean);
            return resource.entity == null ? new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE) : resource.entity;
        } catch (CommonErrorWithDataException e) {
            // TODO 2019-10-13 in case of this exception resources won't be cleaned, need to re-write
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        }
    }

    @SuppressWarnings("TryWithIdenticalCatches")
    @GetMapping(value= "/company-batch-download-origin-file/{companyUniqueId}/{batchId}/{fileName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('MAIN_OPERATOR', 'MAIN_SUPPORT')")
    public HttpEntity<AbstractResource> downloadOriginFile(
            HttpServletRequest request,
            @PathVariable Long companyUniqueId,
            @PathVariable("batchId") Long batchId,
            @SuppressWarnings("unused") @PathVariable("fileName") String fileName) {
        CleanerInfo resource = batchTopLevelService.getBatchOriginFile(batchId);
        if (resource==null) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        }
        request.setAttribute(Consts.RESOURCES_TO_CLEAN, resource.toClean);
        return resource.entity == null ? new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE) : resource.entity;
    }
}
