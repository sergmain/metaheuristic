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

package ai.metaheuristic.ai.dispatcher.batch;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeSelectorService;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.data.BatchData;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeService;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
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
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("DuplicatedCode")
@Controller
@RequestMapping("/dispatcher/batch")
@Slf4j
@Profile("dispatcher")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'MANAGER')")
@RequiredArgsConstructor
public class BatchController {

    private static final String REDIRECT_BATCH_BATCHES = "redirect:/dispatcher/batch/batches";

    private final BatchTopLevelService batchTopLevelService;
    private final UserContextService userContextService;
    private final SourceCodeService sourceCodeService;
    private final SourceCodeSelectorService sourceCodeSelectorService;
    private final DispatcherParamsService dispatcherParamsService;

    @GetMapping("/index")
    public String index() {
        return "dispatcher/batch/index";
    }

    @GetMapping("/batches")
    public String batches(
            @RequestParam(required = false, defaultValue = "false") boolean filterBatches,
            @PageableDefault(size = 20) Pageable pageable,
            @ModelAttribute("errorMessage") final String errorMessage,
            @ModelAttribute("infoMessages") final String infoMessages,
            Model model, Authentication authentication
            ) {
        DispatcherContext context = userContextService.getContext(authentication);
        BatchData.BatchesResult batchesResult = batchTopLevelService.getBatches(pageable, context, false, filterBatches);
        ControllerUtils.addMessagesToModel(model, batchesResult);
        model.addAttribute("result", batchesResult);
        return "dispatcher/batch/batches";
    }

    @PostMapping("/batches-part")
    public String batchesPart(
            @RequestParam(required = false, defaultValue = "false") boolean filterBatches,
            @PageableDefault(size = 20) Pageable pageable,
            Model model, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        BatchData.BatchesResult batchesResult = batchTopLevelService.getBatches(pageable, context, false, filterBatches);
        ControllerUtils.addMessagesToModel(model, batchesResult);
        model.addAttribute("result", batchesResult);
        return "dispatcher/batch/batches :: table";
    }

    @GetMapping(value = "/batch-add")
    public String batchAdd(Model model, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        SourceCodeData.SourceCodesForCompany sourceCodes = sourceCodeSelectorService.getAvailableSourceCodesForCompany(context);
        List<String> uids = dispatcherParamsService.getBatches();
        sourceCodes = new SourceCodeData.SourceCodesForCompany(
                sourceCodes.items.stream().filter(o->uids.contains(o.getUid())).collect(Collectors.toList()));
        ControllerUtils.addMessagesToModel(model, sourceCodes);
        model.addAttribute("result", sourceCodes);
        return "dispatcher/batch/batch-add";
    }

    @GetMapping("/batch-delete/{batchId}")
    public String processResourceDelete(Model model, @PathVariable Long batchId, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        BatchData.BatchExecInfo batchExecInfo = batchTopLevelService.getBatchExecInfo(context, batchId);
        if (batchExecInfo==null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Batch #"+batchId+" wasn't found");
            return REDIRECT_BATCH_BATCHES;
        }

        model.addAttribute("result", batchExecInfo);
        return "dispatcher/batch/batch-delete";
    }

    @PostMapping("/batch-delete-commit")
    public String processResourceDeleteCommit(Long batchId, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        OperationStatusRest r = batchTopLevelService.processResourceDeleteCommit(batchId, context, true);
        if (r.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", r.getErrorMessagesAsList());
        }
        return REDIRECT_BATCH_BATCHES;
    }

    @PostMapping(value = "/batch-upload-from-file")
    public String uploadFile(final MultipartFile file, Long sourceCodeId, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        BatchData.UploadingStatus uploadingStatus = batchTopLevelService.batchUploadFromFile(file, sourceCodeId, context);
        if (uploadingStatus.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", uploadingStatus.getErrorMessagesAsList());
        }
        return REDIRECT_BATCH_BATCHES;
    }

    @GetMapping(value= "/batch-status/{batchId}" )
    public String getProcessingResourceStatus(
            Model model, @PathVariable("batchId") Long batchId, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        BatchData.Status status = batchTopLevelService.getProcessingResourceStatus(batchId, context, false);
        if (status.isErrorMessages()) {
            redirectAttributes.addAttribute("errorMessage", status.getErrorMessages());
            return REDIRECT_BATCH_BATCHES;
        }
        model.addAttribute("batchId", batchId);
        model.addAttribute("console", status.console);
        return "dispatcher/batch/batch-status";
    }

    @GetMapping(value= "/batch-download-result/{batchId}/{fileName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public HttpEntity<AbstractResource> downloadProcessingResult(
            HttpServletRequest request, @PathVariable("batchId") Long batchId,
            @SuppressWarnings("unused") @PathVariable("fileName") String fileName, Authentication authentication) throws IOException {
        DispatcherContext context = userContextService.getContext(authentication);

        final ResponseEntity<AbstractResource> entity;
        try {
            CleanerInfo resource = batchTopLevelService.getBatchProcessingResult(batchId, context, false);
            if (resource==null) {
                return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
            }
            entity = resource.entity;
            request.setAttribute(Consts.RESOURCES_TO_CLEAN, resource.toClean);
        } catch (CommonErrorWithDataException e) {
            // TODO 2019-10-13 in case of this exception resources won't be cleaned, need to re-write
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        }
        return entity;
    }

}
