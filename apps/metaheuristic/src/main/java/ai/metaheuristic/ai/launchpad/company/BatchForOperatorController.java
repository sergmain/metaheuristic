/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.company;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.batch.BatchTopLevelService;
import ai.metaheuristic.ai.launchpad.context.LaunchpadContextService;
import ai.metaheuristic.ai.launchpad.data.BatchData;
import ai.metaheuristic.ai.resource.ResourceWithCleanerInfo;
import ai.metaheuristic.ai.utils.ControllerUtils;
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
import java.io.IOException;

@SuppressWarnings("DuplicatedCode")
@Controller
@RequestMapping("/launchpad/company/batch")
@Slf4j
@Profile("launchpad")
@PreAuthorize("hasAnyRole('MASTER_OPERATOR')")
@RequiredArgsConstructor
public class BatchForOperatorController {

    private final BatchTopLevelService batchTopLevelService;
    private final LaunchpadContextService launchpadContextService;

    @GetMapping("/company-batches/{companyId}")
    public String batches(
            Model model,
            @PageableDefault(size = 20) Pageable pageable,
            @ModelAttribute("errorMessage") final String errorMessage,
            @ModelAttribute("infoMessages") final String infoMessages,
            @PathVariable Long companyId
            ) {
        BatchData.BatchesResult batchesResult = batchTopLevelService.getBatches(pageable, companyId, true);
        ControllerUtils.addMessagesToModel(model, batchesResult);
        model.addAttribute("result", batchesResult);
        model.addAttribute("companyId", companyId);
        return "launchpad/company/batch/company-batches";
    }

    @PostMapping("/company-batches-part/{companyId}")
    public String batchesPart(Model model, @PageableDefault(size = 20) Pageable pageable, @PathVariable Long companyId) {
        BatchData.BatchesResult batchesResult = batchTopLevelService.getBatches(pageable, companyId, true);
        ControllerUtils.addMessagesToModel(model, batchesResult);
        model.addAttribute("result", batchesResult);
        model.addAttribute("companyId", companyId);
        return "launchpad/company/batch/company-batches :: table";
    }

    @GetMapping(value = "/company-batch-add/{companyId}")
    public String batchAdd(Model model, @PathVariable Long companyId) {
        BatchData.PlansForBatchResult plans = batchTopLevelService.getPlansForBatchResult(companyId);
        ControllerUtils.addMessagesToModel(model, plans);
        model.addAttribute("result", plans);
        model.addAttribute("companyId", companyId);
        return "launchpad/company/batch/company-batch-add";
    }

    @GetMapping("/company-batch-delete/{companyId}/{batchId}")
    public String processResourceDelete(
            Model model,
            @PathVariable Long companyId,
            @PathVariable Long batchId, final RedirectAttributes redirectAttributes) {
        BatchData.Status status = batchTopLevelService.getProcessingResourceStatus(batchId, companyId, true);
        if (status.isErrorMessages()) {
            redirectAttributes.addAttribute("errorMessage", status.getErrorMessages());
            return "redirect:/launchpad/company/batch/company-batches/" + companyId;
        }
        model.addAttribute("batchId", batchId);
        model.addAttribute("console", status.console);
        model.addAttribute("isOk", status.ok);
        model.addAttribute("companyId", companyId);
        return "launchpad/company/batch/company-batch-delete";
    }

    @PostMapping("/company-batch-delete-commit/{companyId}")
    public String processResourceDeleteCommit(
            Long batchId,
            @PathVariable Long companyId,
            final RedirectAttributes redirectAttributes) {
        OperationStatusRest r = batchTopLevelService.processResourceDeleteCommit(batchId, companyId, false);
        if (r.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", r.errorMessages);
        }
        return "redirect:/launchpad/company/batch/company-batches/" + companyId;
    }

    @PostMapping(value = "/company-batch-upload-from-file/{companyId}")
    public String uploadFile(
            final MultipartFile file,
            @PathVariable Long companyId,
            Long planId, final RedirectAttributes redirectAttributes, Authentication authentication) {
        // create context with putting current user to specific company
        LaunchpadContext context = launchpadContextService.getContext(authentication, companyId);
        OperationStatusRest r = batchTopLevelService.batchUploadFromFile(file, planId, context);
        if (r.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", r.errorMessages);
        }
        return "redirect:/launchpad/company/batch/company-batches/" + companyId;
    }

    @GetMapping(value= "/company-batch-status/{companyId}/{batchId}" )
    public String getProcessingResourceStatus(
            Model model,
            @PathVariable Long companyId,
            @PathVariable("batchId") Long batchId, final RedirectAttributes redirectAttributes) {
        BatchData.Status status = batchTopLevelService.getProcessingResourceStatus(batchId, companyId, true);
        if (status.isErrorMessages()) {
            redirectAttributes.addAttribute("errorMessage", status.getErrorMessages());
            return "redirect:/launchpad/company/batch/company-batches/" + companyId;
        }
        model.addAttribute("batchId", batchId);
        model.addAttribute("console", status.console);
        model.addAttribute("companyId", companyId);
        return "launchpad/company/batch/company-batch-status";
    }

    @GetMapping(value= "/company-batch-download-result/{companyId}/{batchId}/{fileName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public HttpEntity<AbstractResource> downloadProcessingResult(
            HttpServletRequest request,
            @PathVariable Long companyId,
            @PathVariable("batchId") Long batchId,
            @SuppressWarnings("unused") @PathVariable("fileName") String fileName) throws IOException {
        final ResponseEntity<AbstractResource> entity;
        try {
            ResourceWithCleanerInfo resource = batchTopLevelService.getBatchProcessingResult(batchId, companyId, true);
            if (resource==null) {
                return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
            }
            entity = resource.entity;
            request.setAttribute(Consts.RESOURCES_TO_CLEAN, resource.toClean);
        } catch (BinaryDataNotFoundException e) {
            // TODO 2019-10-13 in case of this exception resources won't be cleaned, need to re-write
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        }
        return entity;
    }

    @SuppressWarnings("TryWithIdenticalCatches")
    @GetMapping(value= "/company-batch-download-origin-file/{companyId}/{batchId}/{fileName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public HttpEntity<AbstractResource> downloadOrignFile(
            HttpServletRequest request,
            @PathVariable Long companyId,
            @PathVariable("batchId") Long batchId,
            @SuppressWarnings("unused") @PathVariable("fileName") String fileName) {
        final ResponseEntity<AbstractResource> entity;
        try {
            ResourceWithCleanerInfo resource = batchTopLevelService.getBatchOriginFile(batchId);
            if (resource==null) {
                return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
            }
            entity = resource.entity;
            request.setAttribute(Consts.RESOURCES_TO_CLEAN, resource.toClean);
        } catch (BinaryDataNotFoundException e) {
            // TODO 2019-10-13 in case of this exception resources won't be cleaned, need to re-write
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        } catch (Throwable e) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        }
        return entity;
    }

}
