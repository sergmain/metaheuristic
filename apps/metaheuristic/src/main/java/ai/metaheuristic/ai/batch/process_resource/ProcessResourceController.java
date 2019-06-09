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

package ai.metaheuristic.ai.batch.process_resource;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.NeedRetryAfterCacheCleanException;
import ai.metaheuristic.ai.exceptions.PilotResourceProcessingException;
import ai.metaheuristic.ai.exceptions.StoreNewFileWithRedirectException;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.launchpad_resource.ResourceService;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.plan.PlanService;
import ai.metaheuristic.ai.launchpad.repositories.PlanRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.batch.beans.Batch;
import ai.metaheuristic.ai.batch.beans.BatchStatus;
import ai.metaheuristic.ai.batch.beans.BatchWorkbook;
import ai.metaheuristic.ai.batch.data.BatchData;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtils;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data.OperationStatusRest;
import ai.metaheuristic.api.v1.data.PlanApiData;
import ai.metaheuristic.api.v1.launchpad.Plan;
import ai.metaheuristic.api.v1.launchpad.Workbook;
import ai.metaheuristic.commons.exceptions.UnzipArchiveException;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/pilot/process-resource")
@Slf4j
@Profile("launchpad")
public class ProcessResourceController {

    private static final String REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES = "redirect:/pilot/process-resource/process-resources";
    private static final String ITEM_LIST_PREFIX = "  - ";
    public static final String MAIN_DOCUMENT_POOL_CODE = "mainDocument";
    private static final String ATTACHMENTS_POOL_CODE = "attachments";
    private static final String RESULT_ZIP = "result.zip";

    private static Set<String> EXCLUDE_EXT = Set.of(".zip", ".yaml");

    private static final String CONFIG_FILE = "config.yaml";

    private final Globals globals;
    private final WorkbookRepository workbookRepository;
    private final PlanRepository planRepository;
    private final PlanCache planCache;
    private final PlanService planService;
    private final ResourceService resourceService;
    private final BatchRepository batchRepository;
    private final BatchWorkbookRepository batchWorkbookRepository;
    private final BinaryDataService binaryDataService;
    private final BatchService batchService;

    @Data
    public static class ProcessResourceResult {
        public Page<BatchData.ProcessResourceItem> items;
    }

    @Data
    public static class PlanListResult {
        public Iterable<Plan> items;
    }

    public ProcessResourceController(WorkbookRepository workbookRepository, PlanRepository planRepository, PlanCache planCache, PlanService planService, ResourceService resourceService, BatchRepository batchRepository, BatchWorkbookRepository batchWorkbookRepository, BinaryDataService binaryDataService, Globals globals, BatchService batchService) {
        this.workbookRepository = workbookRepository;
        this.planRepository = planRepository;
        this.planCache = planCache;
        this.planService = planService;
        this.resourceService = resourceService;
        this.batchRepository = batchRepository;
        this.batchWorkbookRepository = batchWorkbookRepository;
        this.binaryDataService = binaryDataService;
        this.globals = globals;
        this.batchService = batchService;
    }

    private static final String ALLOWED_CHARS_IN_ZIP_REGEXP = "^[/\\\\A-Za-z0-9._-]*$";
    private static final Pattern zipCharsPattern = Pattern.compile(ALLOWED_CHARS_IN_ZIP_REGEXP);

    public static boolean isZipEntityNameOk(String name) {
        Matcher m = zipCharsPattern.matcher(name);
        return m.matches();
    }
    public static final Function<String, Boolean> VALIDATE_ZIP_FUNCTION = ProcessResourceController::isZipEntityNameOk;

    @GetMapping("/process-resources")
    public String plans(@ModelAttribute(name = "result") ProcessResourceResult result, @PageableDefault(size = 5) Pageable pageable,
                        @ModelAttribute("errorMessage") final String errorMessage,
                        @ModelAttribute("infoMessages") final String infoMessages ) {
        prepareProcessResourcesResult(result, pageable);
        return "pilot/process-resource/process-resources";
    }

    @PostMapping("/process-resources-part")
    public String workbooksPart(@ModelAttribute(name = "result") ProcessResourceResult result,
                                    @SuppressWarnings("DefaultAnnotationParam") @PageableDefault(size = 10) Pageable pageable) {
        prepareProcessResourcesResult(result, pageable);
        return "pilot/process-resource/process-resources :: table";
    }

    private void prepareProcessResourcesResult(ProcessResourceResult result, Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(10, pageable);
        Page<Long> batchIds = batchRepository.findAllByOrderByCreatedOnDesc(pageable);

        long total = batchIds.getTotalElements();
        List<BatchData.ProcessResourceItem> items = batchService.getBatches(batchIds);
        result.items = new PageImpl<>(items, pageable, total);

        //noinspection unused
        int i=0;
    }

    @SuppressWarnings("Duplicates")
    @GetMapping(value = "/process-resource-add")
    public String workbookAdd(@ModelAttribute("result") PlanListResult result) {
        result.items = planRepository.findAllAsPlan().stream().filter(o->{
            try {
                PlanApiData.PlanParamsYaml ppy = PlanParamsYamlUtils.to(o.getParams());
                return ppy.internalParams == null || !ppy.internalParams.archived;
            } catch (YAMLException e) {
                log.error("#990.010 Can't parse Plan params. It's broken or unknown version. Plan id: #{}", o.getId());
                log.error("#990.015 Params:\n{}", o.getParams());
                log.error("#990.020 Error: {}", e.toString());
                return false;
            }
        }).collect(Collectors.toList());
        return "pilot/process-resource/process-resource-add";
    }

    @PostMapping(value = "/process-resource-upload-from-file")
    public String uploadFile(final MultipartFile file, Long planId, final RedirectAttributes redirectAttributes) {

        String tempFilename = file.getOriginalFilename();
        if (tempFilename == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.040 name of uploaded file is null");
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
        final String originFilename = tempFilename.toLowerCase();
        Plan plan = planCache.findById(planId);
        if (plan == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.050 plan wasn't found, planId: " + planId);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        // validate the plan
        PlanApiData.PlanValidation planValidation = planService.validateInternal(plan);
        if (planValidation.status != EnumsApi.PlanValidateStatus.OK ) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.060 validation of plan was failed, status: " + planValidation.status);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        try {
            File tempDir = DirUtils.createTempDir("batch-file-upload-");
            if (tempDir==null || tempDir.isFile()) {
                redirectAttributes.addFlashAttribute("errorMessage", "#990.070 can't create temporary directory in " + System.getProperty("java.io.tmpdir"));
                return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
            }

            final File dataFile = new File(tempDir, originFilename );
            log.debug("Start storing an uploaded file to disk");
            try(OutputStream os = new FileOutputStream(dataFile)) {
                IOUtils.copy(file.getInputStream(), os, 32000);
            }

            final Batch b = batchService.createNewBatch(plan.getId());
            try(InputStream is = new FileInputStream(dataFile)) {
                String code = toResourceCode(originFilename);
                binaryDataService.save(
                        is, dataFile.length(), EnumsApi.BinaryDataType.BATCH, code, code,
                        true, originFilename, b.id, EnumsApi.BinaryDataRefType.batch);
            }

            final Batch batch = batchService.changeStateToPreparing(b.id);
            if (batch==null) {
                redirectAttributes.addFlashAttribute("errorMessage","#990.080 can't find batch with id " + b.id);
                return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
            }

            log.info("The file {} was successfully stored to disk", originFilename);
            new Thread(() -> {
                try {
                    if (originFilename.endsWith(".zip")) {

                        List<String> errors = ZipUtils.validate(dataFile, VALIDATE_ZIP_FUNCTION);
                        if (!errors.isEmpty()) {
                            StringBuilder err = new StringBuilder("#990.090 Zip archive contains wrong chars in name(s):\n");
                            for (String error : errors) {
                                err.append('\t').append(error).append('\n');
                            }
                            batchService.changeStateToError(batch.id, err.toString());
                            return;
                        }

                        log.debug("Start unzipping archive");
                        ZipUtils.unzipFolder(dataFile, tempDir);
                        log.debug("Start loading file data to db");
                        loadFilesFromDirAfterZip(batch, tempDir, plan);
                    }
                    else {
                        log.debug("Start loading file data to db");
                        loadFilesFromDirAfterZip(batch, tempDir,  plan);
                    }
                }
                catch(UnzipArchiveException e) {
                    log.error("Error", e);
                    batchService.changeStateToError(batch.id, "#990.100 can't unzip an archive. Error: " + e.getMessage()+", class: " + e.getClass());
                }
                catch(Throwable th) {
                    log.error("Error", th);
                    batchService.changeStateToError(batch.id, "#990.110 General processing error. Error: " + th.getMessage()+", class: " + th.getClass());
                }

            }).start();
            //noinspection unused
            int i=0;
        }
        catch (Throwable th) {
            log.error("Error", th);
            redirectAttributes.addFlashAttribute("errorMessage", "#990.120 can't load file, error: " + th.getMessage()+", class: " + th.getClass());
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
    }

    private void loadFilesFromDirAfterZip(Batch batch, File srcDir, Plan plan) throws IOException {

        List<Path> paths = Files.list(srcDir.toPath())
                .filter(o -> {
                    File f = o.toFile();
                    return !EXCLUDE_EXT.contains(StrUtils.getExtension(f.getName()));
                })
                .collect(Collectors.toList());

        if (paths.isEmpty()) {
            batchService.changeStateToFinished(batch.id);
            return;
        }

        for (Path dataFile : paths) {
            File file = dataFile.toFile();
            if (file.isDirectory()) {
                try {
                    final File mainDocFile = getMainDocumentFile(file);
                    final List<File> files = new ArrayList<>();
                    Files.list(dataFile)
                            .filter(o -> o.toFile().isFile())
                            .forEach(f -> files.add(f.toFile()));

                    createAndProcessTask(batch, plan, files, mainDocFile);
                } catch (StoreNewFileWithRedirectException e) {
                    throw e;
                } catch (Throwable th) {
                    String es = "#990.130 An error while saving data to file, " + th.toString();
                    log.error(es, th);
                    throw new PilotResourceProcessingException(es);
                }
            } else {
                createAndProcessTask(batch, plan, Collections.singletonList(file), file);
            }
        }
    }

    private File getMainDocumentFile(File srcDir) throws IOException {
        File configFile = new File(srcDir, CONFIG_FILE);
        if (!configFile.exists()) {
            throw new PilotResourceProcessingException("#990.140 config.yaml file wasn't found in path " + srcDir.getPath());
        }

        if (!configFile.isFile()) {
            throw new PilotResourceProcessingException("#990.150 config.yaml must be a file, not a directory");
        }
        Yaml yaml = new Yaml();

        String mainDocument;
        try (InputStream is = new FileInputStream(configFile)) {
            Map<String, Object> config = yaml.load(is);
            mainDocument = config.get(MAIN_DOCUMENT_POOL_CODE).toString();
        }

        if (StringUtils.isBlank(mainDocument)) {
            throw new PilotResourceProcessingException("#990.160 config.yaml must contain non-empty field '" + MAIN_DOCUMENT_POOL_CODE + "' ");
        }

        final File mainDocFile = new File(srcDir, mainDocument);
        if (!mainDocFile.exists()) {
            throw new PilotResourceProcessingException("#990.170 main document file "+mainDocument+" wasn't found in path " + srcDir.getPath());
        }
        return mainDocFile;
    }

    private static String asInputResourceParams(String mainPoolCode, String attachPoolCode, List<String> attachmentCodes) {
        String yaml ="preservePoolNames: true\n" +
                "poolCodes:\n  " + MAIN_DOCUMENT_POOL_CODE + ":\n" + ITEM_LIST_PREFIX + mainPoolCode;
        if (attachmentCodes.isEmpty()) {
            return yaml;
        }
        yaml += "\n  " + ATTACHMENTS_POOL_CODE + ":\n" + ITEM_LIST_PREFIX + attachPoolCode + '\n';
        return yaml;
    }

    private void createAndProcessTask(Batch batch, Plan plan, List<File> dataFile, File mainDocFile) {
        long nanoTime = System.nanoTime();
        List<String> attachments = new ArrayList<>();
        String mainPoolCode = String.format("%d-%s-%d", plan.getId(), MAIN_DOCUMENT_POOL_CODE, nanoTime);
        String attachPoolCode = String.format("%d-%s-%d", plan.getId(), ATTACHMENTS_POOL_CODE, nanoTime);
        boolean isMainDocPresent = false;
        for (File file : dataFile) {
            String originFilename = file.getName();
            if (EXCLUDE_EXT.contains(StrUtils.getExtension(originFilename))) {
                continue;
            }
            final String code = toResourceCode(originFilename);

            String poolCode;
            if (file.equals(mainDocFile)) {
                poolCode = mainPoolCode;
                isMainDocPresent = true;
            }
            else {
                poolCode = attachPoolCode;
                attachments.add(code);
            }

            resourceService.storeInitialResource(file, code, poolCode, originFilename);
        }

        if (!isMainDocPresent) {
            throw new PilotResourceProcessingException("#990.180 main document wasn't found");
        }

        final String paramYaml = asInputResourceParams(mainPoolCode, attachPoolCode, attachments);
        PlanApiData.TaskProducingResultComplex producingResult = planService.createWorkbook(plan.getId(), paramYaml);
        if (producingResult.planProducingStatus!= EnumsApi.PlanProducingStatus.OK) {
            throw new PilotResourceProcessingException("#990.190 Error creating workbook: " + producingResult.planProducingStatus);
        }
        BatchWorkbook bfi = new BatchWorkbook();
        bfi.batchId=batch.id;
        bfi.workbookId=producingResult.workbook.getId();
        batchWorkbookRepository.save(bfi);

        // ugly work-around on ObjectOptimisticLockingFailureException, StaleObjectStateException
        Long planId = plan.getId();
        plan = planCache.findById(planId);
        if (plan == null) {
            throw new PilotResourceProcessingException("#990.200 plan wasn't found, planId: " + planId);
        }

        // validate the plan + the workbook
        PlanApiData.PlanValidation planValidation = planService.validateInternal(plan);
        if (planValidation.status != EnumsApi.PlanValidateStatus.OK ) {
            throw new PilotResourceProcessingException("#990.210 validation of plan was failed, status: " + planValidation.status);
        }

        PlanApiData.TaskProducingResultComplex countTasks = planService.produceTasks(false, plan, producingResult.workbook);
        if (countTasks.planProducingStatus != EnumsApi.PlanProducingStatus.OK) {
            throw new PilotResourceProcessingException("#990.220 validation of plan was failed, status: " + countTasks.planValidateStatus);
        }

        if (globals.maxTasksPerPlan < countTasks.numberOfTasks) {
            planService.changeValidStatus(producingResult.workbook, false);
            throw new PilotResourceProcessingException(
                    "#990.220 number of tasks for this workbook exceeded the allowed maximum number. Workbook was created but its status is 'not valid'. " +
                            "Allowed maximum number of tasks: " + globals.maxTasksPerPlan+", tasks in this workbook:  " + countTasks.numberOfTasks);
        }
        planService.changeValidStatus(producingResult.workbook, true);

        // start producing new tasks
        OperationStatusRest operationStatus = planService.workbookTargetExecState(producingResult.workbook.getId(), EnumsApi.WorkbookExecState.PRODUCING);

        if (operationStatus.isErrorMessages()) {
            throw new PilotResourceProcessingException(operationStatus.getErrorMessagesAsStr());
        }
        planService.createAllTasks();


        batchService.changeStateToProcessing(batch.id);
        operationStatus = planService.workbookTargetExecState(producingResult.workbook.getId(), EnumsApi.WorkbookExecState.STARTED);

        if (operationStatus.isErrorMessages()) {
            throw new PilotResourceProcessingException(operationStatus.getErrorMessagesAsStr());
        }
    }

    private static String toResourceCode(String originFilename) {
        long nanoTime = System.nanoTime();
        String name = StrUtils.getName(originFilename);
        String ext = StrUtils.getExtension(originFilename);
        return StringUtils.replaceEach(name, new String[] {" "}, new String[] {"_"} ) + '-' + nanoTime + ext;
    }

    @GetMapping("/process-resource-delete/{batchId}")
    public String processResourceDelete(Model model, @PathVariable Long batchId, final RedirectAttributes redirectAttributes) {

        Batch batch = batchService.findById(batchId);
        if (batch == null) {
            final String es = "#990.230 Batch wasn't found, batchId: " + batchId;
            log.info(es);
            redirectAttributes.addAttribute("errorMessage",es );
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        BatchStatus status;
        try {
            status = batchService.updateStatus(batchId, false);
        } catch (NeedRetryAfterCacheCleanException e) {
            try {
                status = batchService.updateStatus(batchId, false);
            } catch (Throwable th) {
                final String es = "#990.240 Error preparing status for batch #" + batchId+", error: " + th.toString();
                log.error(es, th);
                redirectAttributes.addAttribute("errorMessage", es );
                return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
            }
        }

        model.addAttribute("batchId", batchId);
        model.addAttribute("console", status.getStatus());
        model.addAttribute("isOk", status.ok);
        return "pilot/process-resource/process-resource-delete";
    }

    @PostMapping("/process-resource-delete-commit")
    public String processResourceDeleteCommit(Long batchId, final RedirectAttributes redirectAttributes) {

        Batch batch = batchService.findById(batchId);
        if (batch == null) {
            final String es = "#990.250 Batch wasn't found, batchId: " + batchId;
            log.info(es);
            redirectAttributes.addAttribute("errorMessage",es );
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        List<Long> bfis = batchWorkbookRepository.findWorkbookIdsByBatchId(batch.id);
        for (Long workbookId : bfis) {
            Workbook wb = workbookRepository.findById(workbookId).orElse(null);
            if (wb == null) {
                continue;
            }
            planService.deleteWorkbook(wb.getId(), wb.getPlanId());
        }
        batchWorkbookRepository.deleteByBatchId(batch.id);
        batchService.deleteById(batch.id);

        redirectAttributes.addAttribute("infoMessages", "Batch #"+batch.id+" was deleted successfully.");
        return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
    }

    @GetMapping(value= "/process-resource-status/{batchId}" )
    public String getProcessingResourceStatus(
            Model model, @PathVariable("batchId") Long batchId, final RedirectAttributes redirectAttributes) {

        Batch batch = batchService.findById(batchId);
        if (batch == null) {
            final String es = "#990.260 Batch wasn't found, batchId: " + batchId;
            log.info(es);
            redirectAttributes.addAttribute("errorMessage",es );
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        BatchStatus status = batchService.updateStatus(batchId, false);

        model.addAttribute("batchId", batchId);
        model.addAttribute("console", status.getStatus());
        return "pilot/process-resource/process-resource-status";
    }

    @GetMapping(value= "/process-resource-download-result/{batchId}/{fileName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
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
