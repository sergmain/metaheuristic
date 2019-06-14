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

package ai.metaheuristic.ai.launchpad.batch.process_resource;

import ai.metaheuristic.ai.launchpad.batch.beans.Batch;
import ai.metaheuristic.ai.launchpad.batch.beans.BatchStatus;
import ai.metaheuristic.ai.launchpad.data.BatchData;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.v1.data.OperationStatusRest;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/launchpad/batch")
@Slf4j
@Profile("launchpad")
public class BatchController {

    private static final String REDIRECT_BATCH_BATCHES = "redirect:/launchpad/batch/batches";
    private static final String RESULT_ZIP = "result.zip";

    private final BatchService batchService;
    private final BatchTopLevelService batchTopLevelService;

    public BatchController(BatchService batchService, BatchTopLevelService batchTopLevelService) {
        this.batchService = batchService;
        this.batchTopLevelService = batchTopLevelService;
    }

    @GetMapping("/index")
    public String index() {
        return "launchpad/batch/index";
    }

    @GetMapping("/batches")
    public String batches(
            Model model,
            @PageableDefault(size = 20) Pageable pageable,
            @ModelAttribute("errorMessage") final String errorMessage,
            @ModelAttribute("infoMessages") final String infoMessages ) {
        BatchData.BatchesResult batchesResult = batchTopLevelService.getBatches(pageable);
        ControllerUtils.addMessagesToModel(model, batchesResult);
        model.addAttribute("result", batchesResult);
        return "launchpad/batch/batches";
    }

    @PostMapping("/batches-part")
    public String batchesPart(Model model, @PageableDefault(size = 20) Pageable pageable) {
        BatchData.BatchesResult batchesResult = batchTopLevelService.getBatches(pageable);
        ControllerUtils.addMessagesToModel(model, batchesResult);
        model.addAttribute("result", batchesResult);
        return "launchpad/batch/batches :: table";
    }

    @GetMapping(value = "/batch-add")
    public String batchAdd(Model model) {
        BatchData.PlansForBatchResult plans = batchTopLevelService.getPlansForBatchResult();
        ControllerUtils.addMessagesToModel(model, plans);
        model.addAttribute("result", plans);
        return "launchpad/batch/batch-add";
    }

    @PostMapping(value = "/batch-upload-from-file")
    public String uploadFile(final MultipartFile file, Long planId, final RedirectAttributes redirectAttributes) {
        OperationStatusRest r = batchTopLevelService.batchUploadFromFile(file, planId);
        if (r.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", r.errorMessages);
        }
        return REDIRECT_BATCH_BATCHES;
    }

    @GetMapping(value= "/batch-status/{batchId}" )
    public String getProcessingResourceStatus(
            Model model, @PathVariable("batchId") Long batchId, final RedirectAttributes redirectAttributes) {
        BatchData.Status status = batchTopLevelService.getProcessingResourceStatus(batchId);
        if (status.isErrorMessages()) {
            redirectAttributes.addAttribute("errorMessage", status.getErrorMessages());
            return REDIRECT_BATCH_BATCHES;
        }
        model.addAttribute("batchId", batchId);
        model.addAttribute("console", status.console);
        return "launchpad/batch/batch-status";
    }

    @GetMapping("/batch-delete/{batchId}")
    public String processResourceDelete(Model model, @PathVariable Long batchId, final RedirectAttributes redirectAttributes) {
        BatchData.Status status = batchTopLevelService.getProcessingResourceStatus(batchId);
        if (status.isErrorMessages()) {
            redirectAttributes.addAttribute("errorMessage", status.getErrorMessages());
            return REDIRECT_BATCH_BATCHES;
        }
        model.addAttribute("batchId", batchId);
        model.addAttribute("console", status.console);
        model.addAttribute("isOk", status.ok);
        return "launchpad/batch/batch-delete";
    }

    @PostMapping("/batch-delete-commit")
    public String processResourceDeleteCommit(Long batchId, final RedirectAttributes redirectAttributes) {
        OperationStatusRest r = batchTopLevelService.processResourceDeleteCommit(batchId);
        if (r.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", r.errorMessages);
        }
        return REDIRECT_BATCH_BATCHES;
    }

    @GetMapping(value= "/batch-download-result/{batchId}/{fileName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public HttpEntity<AbstractResource> downloadProcessingResult(
            HttpServletResponse response, @PathVariable("batchId") Long batchId,
            @SuppressWarnings("unused") @PathVariable("fileName") String fileName/*, final RedirectAttributes redirectAttributes*/) throws IOException {

        File resultDir = DirUtils.createTempDir("prepare-file-processing-result-");
        File zipDir = new File(resultDir, "zip");

        Batch batch = batchService.findById(batchId);
        if (batch == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            log.info("#990.270 Batch wasn't found, batchId: {}", batchId);
            return null;
        }

        BatchStatus status = batchService.prepareStatusAndData(batchId, zipDir, false, true);

        File statusFile = new File(zipDir, "status.txt");
        FileUtils.write(statusFile, status.getStatus(), StandardCharsets.UTF_8);
        File zipFile = new File(resultDir, RESULT_ZIP);
        ZipUtils.createZip(zipDir, zipFile);


        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        httpHeaders.setContentDispositionFormData("attachment", RESULT_ZIP);
        return new HttpEntity<>(new FileSystemResource(zipFile), getHeader(httpHeaders, zipFile.length()));
    }

    private static HttpHeaders getHeader(HttpHeaders httpHeaders, long length) {
        HttpHeaders header = httpHeaders != null ? httpHeaders : new HttpHeaders();
        header.setContentLength(length);
        header.setCacheControl("max-age=0");
        header.setExpires(0);
        header.setPragma("no-cache");

        return header;
    }

}
