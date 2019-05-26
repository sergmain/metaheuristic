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

package ai.metaheuristic.ai.pilot.process_resource;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.ai.exceptions.PilotResourceProcessingException;
import ai.metaheuristic.ai.exceptions.StoreNewFileWithRedirectException;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.launchpad_resource.ResourceService;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.plan.PlanService;
import ai.metaheuristic.ai.launchpad.repositories.PlanRepository;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.pilot.beans.Batch;
import ai.metaheuristic.ai.pilot.beans.BatchWorkbook;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.input_resource_param.InputResourceParamUtils;
import ai.metaheuristic.ai.yaml.plan.PlanYamlUtils;
import ai.metaheuristic.ai.yaml.snippet_exec.SnippetExecUtils;
import ai.metaheuristic.ai.yaml.task.TaskParamYamlUtils;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data.*;
import ai.metaheuristic.api.v1.launchpad.Plan;
import ai.metaheuristic.api.v1.launchpad.Task;
import ai.metaheuristic.api.v1.launchpad.Workbook;
import ai.metaheuristic.commons.exceptions.UnzipArchiveException;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
import java.util.stream.Collectors;

@Controller
@RequestMapping("/pilot/process-resource")
@Slf4j
@Profile("launchpad")
public class ProcessResourceController {

    private static final String REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES = "redirect:/pilot/process-resource/process-resources";
    private static final String ITEM_LIST_PREFIX = "  - ";
    private static final String MAIN_DOCUMENT_POOL_CODE = "mainDocument";
    private static final String ATTACHMENTS_POOL_CODE = "attachments";
    private static final String RESULT_ZIP = "result.zip";
    private static final String PLAN_NOT_FOUND = "Plan wasn't found";

    private static Set<String> EXCLUDE_EXT = Set.of(".zip", ".yaml");

    private static final String CONFIG_FILE = "config.yaml";

    private final Globals globals;
    private final WorkbookRepository workbookRepository;
    private final PlanRepository planRepository;
    private final PlanCache planCache;
    private final PlanService planService;
    private final ResourceService resourceService;
    private final TaskRepository taskRepository;
    private final BatchRepository batchRepository;
    private final BatchWorkbookRepository batchWorkbookRepository;
    private final BinaryDataService binaryDataService;
    private final BatchCache batchCache;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessResourceItem {
        public Batch batch;
        public String planCode;
        public String execStateStr;
        public int execState;
        public boolean ok;
    }

    @Data
    public static class ProcessResourceResult {
        public Page<ProcessResourceItem> items;
    }

    @Data
    public static class PlanListResult {
        public Iterable<Plan> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchStatus {
        public final StringBuilder sb = new StringBuilder();
        public boolean ok = false;

        public void add(String status) {
            sb.append(status);
        }

        public void add(String status, char c) {
            sb.append(status);
            sb.append(c);
        }

        public String getStatus() {
            return sb.toString();
        }

        public void add(char c) {
            sb.append(c);
        }
    }

    public ProcessResourceController(WorkbookRepository workbookRepository, PlanRepository planRepository, PlanCache planCache, PlanService planService, ResourceService resourceService, TaskRepository taskRepository, BatchRepository batchRepository, BatchWorkbookRepository batchWorkbookRepository, BinaryDataService binaryDataService, Globals globals, BatchCache batchCache) {
        this.workbookRepository = workbookRepository;
        this.planRepository = planRepository;
        this.planCache = planCache;
        this.planService = planService;
        this.resourceService = resourceService;
        this.taskRepository = taskRepository;
        this.batchRepository = batchRepository;
        this.batchWorkbookRepository = batchWorkbookRepository;
        this.binaryDataService = binaryDataService;
        this.globals = globals;
        this.batchCache = batchCache;
    }

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

        List<ProcessResourceItem> items = new ArrayList<>();
        long total = batchIds.getTotalElements();
        for (Long batchId : batchIds) {
            Batch batch = batchCache.findById(batchId);
            if (batch.execState!= Enums.BatchExecState.Finished.code && batch.execState!= Enums.BatchExecState.Archived.code) {
                Boolean isFinished = null;
                for (Workbook fi : workbookRepository.findWorkbookByBatchId(batch.id)) {
                    isFinished = Boolean.TRUE;
                    if (fi.getExecState() != EnumsApi.WorkbookExecState.ERROR.code &&
                            fi.getExecState() != EnumsApi.WorkbookExecState.FINISHED.code) {
                        isFinished = Boolean.FALSE;
                        break;
                    }
                }
                if (Boolean.TRUE.equals(isFinished)) {
                    batch.setExecState(Enums.BatchExecState.Finished.code);
                    batch = batchCache.save(batch);

                }
            }
            BatchStatus status = prepareStatusAndData(batch.id, null, false, false);
            boolean ok = status.ok;
            String planCode = PLAN_NOT_FOUND;
            Plan plan = planCache.findById(batch.getPlanId());
            if (plan!=null) {
                planCode = plan.getCode();
            }
            else {
                if (batch.execState!=Enums.BatchExecState.Preparing.code) {
                    ok = false;
                }
            }
            // fix current state in case when data is preparing right now
            if (batch.execState==Enums.BatchExecState.Preparing.code) {
                ok = true;
            }
            String execStateStr = Enums.BatchExecState.toState(batch.execState).toString();
            items.add( new ProcessResourceItem(batch, planCode, execStateStr, batch.execState, ok));
        }
        result.items = new PageImpl<>(items, pageable, total);

        //noinspection unused
        int i=0;
    }

    @GetMapping(value = "/process-resource-add")
    public String workbookAdd(@ModelAttribute("result") PlanListResult result) {
        result.items = planRepository.findAllAsPlan();
        return "pilot/process-resource/process-resource-add";
    }

    @PostMapping(value = "/process-resource-upload-from-file")
    public String uploadFile(final MultipartFile file, Long planId, final RedirectAttributes redirectAttributes) {

        String tempFilename = file.getOriginalFilename();
        if (tempFilename == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.10 name of uploaded file is null");
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
        final String originFilename = tempFilename.toLowerCase();
        Plan plan = planCache.findById(planId);
        if (plan == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.31 plan wasn't found, planId: " + planId);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        // validate the plan
        PlanApiData.PlanValidation planValidation = planService.validateInternal(plan);
        if (planValidation.status != EnumsApi.PlanValidateStatus.OK ) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.37 validation of plan was failed, status: " + planValidation.status);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        try {
            File tempDir = DirUtils.createTempDir("batch-file-upload-");
            if (tempDir==null || tempDir.isFile()) {
                redirectAttributes.addFlashAttribute("errorMessage", "#990.24 can't create temporary directory in " + System.getProperty("java.io.tmpdir"));
                return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
            }

            final File dataFile = new File(tempDir, originFilename );
            log.debug("Start storing an uploaded file to disk");
            try(OutputStream os = new FileOutputStream(dataFile)) {
                IOUtils.copy(file.getInputStream(), os, 32000);
            }

            final Batch b = batchCache.save(new Batch(plan.getId(), Enums.BatchExecState.Stored));
            try(InputStream is = new FileInputStream(dataFile)) {
                String code = toResourceCode(originFilename);
                binaryDataService.save(
                        is, dataFile.length(), EnumsApi.BinaryDataType.BATCH, code, code,
                        true, originFilename, b.id, EnumsApi.BinaryDataRefType.batch);
            }

            b.setExecState(Enums.BatchExecState.Preparing.code);
            final Batch batch = batchCache.save(b);

            log.info("The file {} was successfully stored to disk", originFilename);
            new Thread(() -> {
                try {
                    if (originFilename.endsWith(".zip")) {
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
                    storeErrorToBatch(batch, "#990.65 can't unzip an archive. Error: " + e.getMessage()+", class: " + e.getClass());
                }
                catch(Throwable th) {
                    log.error("Error", th);
                    storeErrorToBatch(batch, "#990.65 General processing error. Error: " + th.getMessage()+", class: " + th.getClass());
                }

            }).start();
            int i=0;
        }
        catch (Throwable th) {
            log.error("Error", th);
            redirectAttributes.addFlashAttribute("errorMessage", "#990.73 can't load file, error: " + th.getMessage()+", class: " + th.getClass());
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
    }

    private Batch storeErrorToBatch(Batch batch, String error) {
        Batch b = batchCache.findById(batch.id);
        if (b==null) {
            log.warn("#990.410 batch not found in db, batchId: #{}", batch.id);
            return null;
        }
        b.setExecState(Enums.BatchExecState.Error.code);
        // TODO 2019.05.25 add storing of error message
        b = batchCache.save(b);
        return b;
    }

    private void loadFilesFromDirAfterZip(Batch batch, File srcDir, Plan plan) throws IOException {

        List<Path> paths = Files.list(srcDir.toPath())
                .filter(o -> {
                    File f = o.toFile();
                    return !EXCLUDE_EXT.contains(StrUtils.getExtension(f.getName()));
                })
                .collect(Collectors.toList());

        if (paths.isEmpty()) {
            batch.setExecState(Enums.BatchExecState.Finished.code);
            batchCache.save(batch);
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
                    String es = "#990.25 An error while saving data to file, " + th.toString();
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
            throw new PilotResourceProcessingException("#990.18 config.yaml file wasn't found in path " + srcDir.getPath());
        }

        if (!configFile.isFile()) {
            throw new PilotResourceProcessingException("#990.19 config.yaml must be a file, not a directory");
        }
        Yaml yaml = new Yaml();

        String mainDocument;
        try (InputStream is = new FileInputStream(configFile)) {
            Map<String, Object> config = yaml.load(is);
            mainDocument = config.get(MAIN_DOCUMENT_POOL_CODE).toString();
        }

        if (StringUtils.isBlank(mainDocument)) {
            throw new PilotResourceProcessingException("#990.17 config.yaml must contain non-empty field '" + MAIN_DOCUMENT_POOL_CODE + "' ");
        }

        final File mainDocFile = new File(srcDir, mainDocument);
        if (!mainDocFile.exists()) {
            throw new PilotResourceProcessingException("#990.16 main document file "+mainDocument+" wasn't found in path " + srcDir.getPath());
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
            throw new PilotResourceProcessingException("#990.28 main document wasn't found");
        }

        final String paramYaml = asInputResourceParams(mainPoolCode, attachPoolCode, attachments);
        PlanApiData.TaskProducingResultComplex producingResult = planService.createWorkbook(plan.getId(), paramYaml);
        if (producingResult.planProducingStatus!= EnumsApi.PlanProducingStatus.OK) {
            throw new PilotResourceProcessingException("#990.42 Error creating workbook: " + producingResult.planProducingStatus);
        }
        BatchWorkbook bfi = new BatchWorkbook();
        bfi.batchId=batch.id;
        bfi.workbookId=producingResult.workbook.getId();
        batchWorkbookRepository.save(bfi);

        // ugly work-around on StaleObjectStateException
        Long planId = plan.getId();
        plan = planCache.findById(planId);
        if (plan == null) {
            throw new PilotResourceProcessingException("#990.49 plan wasn't found, planId: " + planId);
        }

        // validate the plan + the workbook
        PlanApiData.PlanValidation planValidation = planService.validateInternal(plan);
        if (planValidation.status != EnumsApi.PlanValidateStatus.OK ) {
            throw new PilotResourceProcessingException("#990.55 validation of plan was failed, status: " + planValidation.status);
        }

        PlanApiData.TaskProducingResultComplex countTasks = planService.produceTasks(false, plan, producingResult.workbook);
        if (countTasks.planProducingStatus != EnumsApi.PlanProducingStatus.OK) {
            throw new PilotResourceProcessingException("#990.60 validation of plan was failed, status: " + countTasks.planValidateStatus);
        }

        if (globals.maxTasksPerPlan < countTasks.numberOfTasks) {
            planService.changeValidStatus(producingResult.workbook, false);
            throw new PilotResourceProcessingException(
                    "#990.67 number of tasks for this workbook exceeded the allowed maximum number. Workbook was created but its status is 'not valid'. " +
                            "Allowed maximum number of tasks: " + globals.maxTasksPerPlan+", tasks in this workbook:  " + countTasks.numberOfTasks);
        }
        planService.changeValidStatus(producingResult.workbook, true);

        // start producing new tasks
        OperationStatusRest operationStatus = planService.workbookTargetExecState(producingResult.workbook.getId(), EnumsApi.WorkbookExecState.PRODUCING);

        if (operationStatus.isErrorMessages()) {
            throw new PilotResourceProcessingException(operationStatus.getErrorMessagesAsStr());
        }
        planService.createAllTasks();

        Batch b = batchCache.findById(batch.id);
        if (b==null) {
            log.error("#990.105 Batch is null");
        }
        else {
            if (b.execState!=Enums.BatchExecState.Processing.code) {
                b.setExecState(Enums.BatchExecState.Processing.code);
                try {
                    batchCache.save(b);
                } catch (ObjectOptimisticLockingFailureException e) {
                    throw e;
                }
            }
        }
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

        Batch batch = batchCache.findById(batchId);
        if (batch == null) {
            final String es = "#990.109 Batch wasn't found, batchId: " + batchId;
            log.info(es);
            redirectAttributes.addAttribute("errorMessage",es );
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

//        File resultDir = DirUtils.createTempDir("prepare-file-processing-result-");
//        File zipDir = new File(resultDir, "zip");
        BatchStatus status = prepareStatusAndData(batchId, null, false, false);
/*
        // TODO 2019.05.25 Actually, status must never be null
        if (status==null) {
            final String es = "#990.120 Status can't be prepared, batchId: " + batchId;
            log.info(es);
            redirectAttributes.addAttribute("errorMessage",es );
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
*/

        model.addAttribute("batchId", batchId);
        model.addAttribute("console", status.getStatus());
        model.addAttribute("isOk", status.ok);
        return "pilot/process-resource/process-resource-delete";
    }

    @PostMapping("/process-resource-delete-commit")
    public String processResourceDeleteCommit(Long batchId, final RedirectAttributes redirectAttributes) {

        Batch batch = batchCache.findById(batchId);
        if (batch == null) {
            final String es = "#990.209 Batch wasn't found, batchId: " + batchId;
            log.info(es);
            redirectAttributes.addAttribute("errorMessage",es );
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        List<BatchWorkbook> bfis = batchWorkbookRepository.findAllByBatchId(batch.id);
        for (BatchWorkbook bfi : bfis) {
            Workbook wb = workbookRepository.findById(bfi.workbookId).orElse(null);
            if (wb == null) {
                continue;
            }
            planService.deleteWorkbook(wb.getId(), wb.getPlanId());
        }
        batchWorkbookRepository.deleteByBatchId(batch.id);
        batchCache.deleteById(batch.id);

        redirectAttributes.addAttribute("infoMessages", "Batch #"+batch.id+" was deleted successfully.");
        return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
    }

    @GetMapping(value= "/process-resource-status/{batchId}" )
    public String getProcessingResourceStatus(
            Model model, @PathVariable("batchId") Long batchId, final RedirectAttributes redirectAttributes) {

        Batch batch = batchCache.findById(batchId);
        if (batch == null) {
            final String es = "#990.209 Batch wasn't found, batchId: " + batchId;
            log.info(es);
            redirectAttributes.addAttribute("errorMessage",es );
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        File resultDir = DirUtils.createTempDir("prepare-file-processing-result-");
        File zipDir = new File(resultDir, "zip");
        BatchStatus status = prepareStatusAndData(batchId, null, true, false);
/*
        // TODO 2019.05.25 Actually, status must never be null
        if (status==null) {
            final String es = "#990.220 Status can't be prepared, batchId: " + batchId;
            log.info(es);
            redirectAttributes.addAttribute("errorMessage",es );
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
*/

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

        Batch batch = batchCache.findById(batchId);
        if (batch == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            log.info("#990.109 Batch wasn't found, batchId: {}", batchId);
            return null;
        }

        BatchStatus status = prepareStatusAndData(batchId, zipDir, false, true);
        // 2019.05.25 Actually, status must never be null
        if (status==null) {
            return null;
        }

        File statusFile = new File(zipDir, "status.txt");
        FileUtils.write(statusFile, status.getStatus(), StandardCharsets.UTF_8);
        File zipFile = new File(resultDir, RESULT_ZIP);
        ZipUtils.createZip(zipDir, zipFile);


        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        httpHeaders.setContentDispositionFormData("attachment", RESULT_ZIP);
        return new HttpEntity<>(new FileSystemResource(zipFile), getHeader(httpHeaders, zipFile.length()));
    }

    private BatchStatus prepareStatusAndData(Long batchId, File zipDir, boolean fullConsole, boolean storeToDisk)  {
        BatchStatus bs = new BatchStatus();
        log.info("#990.105 Start preparing data, batchId: {}", batchId);

        List<BatchWorkbook> bfis = batchWorkbookRepository.findAllByBatchId(batchId);
        if (bfis.isEmpty()) {
            bs.add("#990.107, Batch is empty, there isn't any task, batchId: " + batchId, '\n');
            bs.ok = true;
            return bs;
        }

        boolean isOk = true;
        for (BatchWorkbook bfi : bfis) {
            Workbook wb = workbookRepository.findById(bfi.workbookId).orElse(null);
            if (wb == null) {
                String msg = "#990.114 Batch #" + batchId + " contains broken workbookId - #" + bfi.workbookId;
                bs.add(msg, '\n');
                log.warn(msg);
                isOk = false;
                continue;
            }
            String mainDocumentPoolCode = getMainDocumentPoolCode(wb.getInputResourceParam());

            final String fullMainDocument = getMainDocumentForPoolCode(mainDocumentPoolCode);
            if (fullMainDocument == null) {
                String msg = "#990.123, " + mainDocumentPoolCode + ", Can't determine actual file name of main document, " +
                        "batchId: " + batchId + ", workbookId: " + bfi.workbookId;
                log.warn(msg);
                bs.add(msg, '\n');
                isOk = false;
                continue;
            }
            String mainDocument = StrUtils.getName(fullMainDocument) + getActualExtension(wb.getPlanId());

            Integer taskOrder = taskRepository.findMaxConcreteOrder(wb.getId());
            // TODO 2019-05-23 investigate all cases when this is happened
            if (taskOrder == null) {
                String msg = "#990.128, " + mainDocument + ", Tasks weren't created correctly for this batch, need to re-upload documents, " +
                        "batchId: " + batchId + ", workbookId: " + bfi.workbookId;
                log.warn(msg);
                bs.add(msg, '\n');
                isOk = false;
                continue;
            }
            List<Task> tasks = taskRepository.findAnyWithConcreteOrder(wb.getId(), taskOrder);
            if (tasks.isEmpty()) {
                String msg = "#990.133, " + mainDocument + ", Can't find any task for batchId: " + batchId;
                log.info(msg);
                bs.add(msg,'\n');
                isOk = false;
                continue;
            }
            if (tasks.size() > 1) {
                String msg = "#990.137, " + mainDocument + ", Can't download file because there are more than one task " +
                        "at the final state, batchId: " + batchId + ", workbookId: " + wb.getId();
                log.info(msg);
                bs.add(msg,'\n');
                isOk = false;
                continue;
            }
            final Task task = tasks.get(0);
            EnumsApi.TaskExecState execState = EnumsApi.TaskExecState.from(task.getExecState());
            SnippetApiData.SnippetExec snippetExec;
            try {
                snippetExec = SnippetExecUtils.to(task.getSnippetExecResults());
            } catch (YAMLException e) {
                bs.add("#990.139, " + mainDocument + ", Task has broken console output, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
                        ", batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                        "taskId: " + task.getId(),'\n');
                isOk = false;
                continue;
            }
            switch (execState) {
                case NONE:
                case IN_PROGRESS:
                    bs.add("#990.142, " + mainDocument + ", Task hasn't completed yet, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
                            ", batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                            "taskId: " + task.getId(),'\n');
                    isOk = true;
                    continue;
                case ERROR:
                    bs.add("#990.149, " + mainDocument + ", Task was completed with error, batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                            "taskId: " + task.getId() + "\n" +
                            "isOk: " + snippetExec.exec.isOk + "\n" +
                            "exitCode: " + snippetExec.exec.exitCode + "\n" +
                            "console:\n" + (StringUtils.isNotBlank(snippetExec.exec.console) ? snippetExec.exec.console : "<output to console is blank>") + "\n\n");
                    isOk = true;
                    continue;
                case OK:
                    if (fullConsole) {
                        bs.add("#990.151, " + mainDocument + ", Task completed without any error, batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                                "taskId: " + task.getId() + "\n" +
                                "isOk: " + snippetExec.exec.isOk + "\n" +
                                "exitCode: " + snippetExec.exec.exitCode + "\n" +
                                "console:\n" + (StringUtils.isNotBlank(snippetExec.exec.console) ? snippetExec.exec.console : "<output to console is blank>") + "\n\n");
                    }
                    isOk = true;
                    // !!! Don't change to continue;
                    break;
            }

            final TaskApiData.TaskParamYaml taskParamYaml;
            try {
                taskParamYaml = TaskParamYamlUtils.toTaskYaml(task.getParams());
            } catch (YAMLException e) {
                bs.add("#990.153, " + mainDocument + ", Task has broken data in params, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
                        ", batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                        "taskId: " + task.getId(), '\n');
                isOk = false;
                continue;
            }

            if (wb.getExecState() != EnumsApi.WorkbookExecState.FINISHED.code) {
                bs.add("#990.155, " + mainDocument + ", Task hasn't completed yet, " +
                        "batchId:" + batchId + ", workbookId: " + wb.getId() + ", " +
                        "taskId: " + task.getId(),'\n');
                isOk = true;
                continue;
            }

            File mainDocFile = zipDir!=null ? new File(zipDir, mainDocument) : new File(mainDocument);

            if (storeToDisk) {
                try {
                    binaryDataService.storeToFile(taskParamYaml.outputResourceCode, mainDocFile);
                } catch (BinaryDataNotFoundException e) {
                    String msg = "#990.161 Error store data to temp file, data doesn't exist in db, code " + taskParamYaml.outputResourceCode +
                            ", file: " + mainDocFile.getPath();
                    log.error(msg);
                    bs.add(msg,'\n');
                    isOk = false;
                    continue;
                }
            }

            if (!fullConsole) {
                String msg = "#990.167 status - Ok, doc: " + mainDocFile.getName() + ", batchId: " + batchId + ", workbookId: " + bfi.workbookId;
                bs.add(msg,'\n');
                isOk = true;
            }

        }
        bs.ok = isOk;
        return bs;
    }

    private String getActualExtension(Long planId) {
        Plan plan = planCache.findById(planId);
        if (plan == null) {
            return (StringUtils.isNotBlank(globals.defaultResultFileExtension)
                    ? globals.defaultResultFileExtension
                    : ".bin(???)");
        }

        PlanApiData.PlanYaml planYaml = PlanYamlUtils.toPlanYaml(plan.getParams());
        Meta meta = planYaml.getMeta(Consts.RESULT_FILE_EXTENSION);

        return meta != null && StringUtils.isNotBlank(meta.getValue())
                ? meta.getValue()
                :
                (StringUtils.isNotBlank(globals.defaultResultFileExtension)
                        ? globals.defaultResultFileExtension
                        : ".bin");
    }

    private String getMainDocumentForPoolCode(String mainDocumentPoolCode) {
        final String filename = binaryDataService.getFilenameByPool1CodeAndType(mainDocumentPoolCode, EnumsApi.BinaryDataType.DATA);
        if (StringUtils.isBlank(filename)) {
            log.error("#990.15 Filename is blank for poolCode: {}, data type: {}", mainDocumentPoolCode, EnumsApi.BinaryDataType.DATA);
            return null;
        }
        return filename;
    }

    private static HttpHeaders getHeader(HttpHeaders httpHeaders, long length) {
        HttpHeaders header = httpHeaders != null ? httpHeaders : new HttpHeaders();
        header.setContentLength(length);
        header.setCacheControl("max-age=0");
        header.setExpires(0);
        header.setPragma("no-cache");

        return header;
    }

    private static String getMainDocumentPoolCode(String inputResourceParams) {
        InputResourceParam resourceParams = InputResourceParamUtils.to(inputResourceParams);
        List<String> codes = resourceParams.poolCodes.get(MAIN_DOCUMENT_POOL_CODE);
        if (codes.isEmpty()) {
            throw new IllegalStateException("#990.92 Main document section is missed. inputResourceParams:\n" + inputResourceParams);
        }
        if (codes.size()>1) {
            throw new IllegalStateException("#990.92 Main document section contains more than one main document. inputResourceParams:\n" + inputResourceParams);
        }
        return codes.get(0);
    }

}
