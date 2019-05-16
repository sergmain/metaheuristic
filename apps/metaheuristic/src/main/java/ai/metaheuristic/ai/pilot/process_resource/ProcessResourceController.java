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
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.ai.exceptions.StoreNewFileException;
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
import ai.metaheuristic.api.v1.launchpad.BinaryData;
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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.yaml.snakeyaml.Yaml;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

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

    private static Set<String> EXCLUDE_EXT = Set.of(".zip", ".yaml");

    private static final String CONFIG_FILE = "config.yaml";

    private final WorkbookRepository workbookRepository;
    private final PlanRepository planRepository;
    private final PlanCache planCache;
    private final PlanService planService;
    private final ResourceService resourceService;
    private final TaskRepository taskRepository;
    private final BatchRepository batchRepository;
    private final BatchWorkbookRepository batchWorkbookRepository;
    private final BinaryDataService binaryDataService;
    private final PlanYamlUtils planYamlUtils;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessResourceItem {
        public Batch batch;
        public Plan plan;
        public String execStateStr;
        public boolean finished;
    }

    @Data
    public static class ProcessResourceResult {
        public Page<ProcessResourceItem> items;
    }

    @Data
    public static class PlanListResult {
        public Iterable<Plan> items;
    }

    private final Globals globals;

    public ProcessResourceController(WorkbookRepository workbookRepository, PlanRepository planRepository, PlanCache planCache, PlanService planService, ResourceService resourceService, TaskRepository taskRepository, BatchRepository batchRepository, BatchWorkbookRepository batchWorkbookRepository, BinaryDataService binaryDataService, PlanYamlUtils planYamlUtils, Globals globals) {
        this.workbookRepository = workbookRepository;
        this.planRepository = planRepository;
        this.planCache = planCache;
        this.planService = planService;
        this.resourceService = resourceService;
        this.taskRepository = taskRepository;
        this.batchRepository = batchRepository;
        this.batchWorkbookRepository = batchWorkbookRepository;
        this.binaryDataService = binaryDataService;
        this.planYamlUtils = planYamlUtils;
        this.globals = globals;
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
        pageable = ControllerUtils.fixPageSize(100, pageable);
        Page<Batch> batches = batchRepository.findAllByOrderByCreatedOnDesc(pageable);

        List<ProcessResourceItem> items = new ArrayList<>();
        long total = batches.getTotalElements();
        for (Batch batch : batches) {
            Plan plan = planCache.findById(batch.getPlanId());
            if (plan==null) {
                log.warn("#990.01 Found batch with wrong planId. planId: {}", batch.getPlanId());
                total--;
                continue;
            }
            List<BatchWorkbook> bfis = batchWorkbookRepository.findAllByBatchId(batch.id);
            boolean isFinished = true;
            for (BatchWorkbook bfi : bfis) {
                Workbook fi = workbookRepository.findById(bfi.workbookId).orElse(null);
                if (fi==null) {
                    String msg = "#990.03 Batch #" + batch.id + " contains broken workbookId - #" + bfi.workbookId;
                    log.warn(msg);
                    continue;
                }
                if (fi.getExecState() != EnumsApi.WorkbookExecState.ERROR.code &&
                        fi.getExecState() != EnumsApi.WorkbookExecState.FINISHED.code) {
                    isFinished = false;
                }
            }
            String execStateStr = isFinished ? "FINISHED" : "IN PROGRESS";
            items.add( new ProcessResourceItem(batch, plan, execStateStr, isFinished));
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

        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.10 name of uploaded file is null");
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
        originFilename = originFilename.toLowerCase();
/*
        if (!StringUtils.endsWithAny(originFilename, ".xml", ".zip", ".doc", ".docx", ".rtf")) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.20 only '.xml' and .zip files are supported, filename: " + originFilename);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
*/
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

            Batch batch = batchRepository.save(new Batch(plan.getId()));


            log.info("The file {} was successfully uploaded", originFilename);

            if (originFilename.endsWith(".zip")) {
                log.debug("Start unzipping archive");
                ZipUtils.unzipFolder(dataFile, tempDir);
                log.debug("Start loading file data to db");
                loadFilesFromDirAfterZip(batch, tempDir, redirectAttributes, plan);
            }
            else {
                log.debug("Start loading file data to db");
                loadFilesFromDirAfterZip(batch, tempDir, redirectAttributes, plan);
            }
        }
        catch(StoreNewFileWithRedirectException e) {
            return e.redirect;
        }
        catch(UnzipArchiveException e) {
            log.error("Error", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "#990.65 can't unzip an archive. Error: " + e.getMessage()+", class: " + e.getClass());
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
        catch (Throwable th) {
            log.error("Error", th);
            redirectAttributes.addFlashAttribute("errorMessage", "#990.73 can't load file, error: " + th.getMessage()+", class: " + th.getClass());
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
    }

    private void loadFilesFromDirAfterZip(Batch batch, File srcDir, RedirectAttributes redirectAttributes, Plan plan) throws IOException {

        Files.list(srcDir.toPath())
                .filter( o -> {
                    File f = o.toFile();
                    return !EXCLUDE_EXT.contains(StrUtils.getExtension(f.getName()));
                })
                .forEach(dataFile -> {
                    File file = dataFile.toFile();
                    if (file.isDirectory()) {
                        try {
                            final File mainDocFile = getMainDocumentFile(file, redirectAttributes);
                            final List<File> files = new ArrayList<>();
                            Files.list(dataFile)
                                    .filter(o -> o.toFile().isFile())
                                    .forEach(f -> files.add(f.toFile()));

                            String redirectUrl1 = createAndProcessTask(batch, redirectAttributes, plan, files, mainDocFile);
                            if (redirectUrl1 != null) {
                                throw new StoreNewFileWithRedirectException(redirectUrl1);
                            }
                        }
                        catch (StoreNewFileWithRedirectException e) {
                                throw e;
                        }
                        catch (Throwable th) {
                            log.error("Error", th);
                            redirectAttributes.addFlashAttribute("errorMessage", "#990.25 An error while saving data to file, " + th.toString());
                            throw new StoreNewFileWithRedirectException(REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES);
                        }
                    }
                    else {
                        String redirectUrl1 = createAndProcessTask(batch, redirectAttributes, plan, Collections.singletonList(file), file);
                        if (redirectUrl1 != null) {
                            throw new StoreNewFileWithRedirectException(redirectUrl1);
                        }
                    }
                });
    }

    private File getMainDocumentFile(File srcDir, RedirectAttributes redirectAttributes) throws IOException {
        File configFile = new File(srcDir, CONFIG_FILE);
        if (!configFile.exists()) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.18 config.yaml file wasn't found in path " + srcDir.getPath());
            throw new StoreNewFileWithRedirectException(REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES);
        }

        if (!configFile.isFile()) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.19 config.yaml must be a file, not a directory");
            throw new StoreNewFileWithRedirectException(REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES);
        }
        Yaml yaml = new Yaml();

        String mainDocument;
        try (InputStream is = new FileInputStream(configFile)) {
            Map<String, Object> config = yaml.load(is);
            mainDocument = config.get(MAIN_DOCUMENT_POOL_CODE).toString();
        }

        if (StringUtils.isBlank(mainDocument)) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.17 config.yaml must contain non-empty field '" + MAIN_DOCUMENT_POOL_CODE + "' ");
            throw new StoreNewFileWithRedirectException(REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES);
        }

        final File mainDocFile = new File(srcDir, mainDocument);
        if (!mainDocFile.exists()) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.16 main document file "+mainDocument+" wasn't found in path " + srcDir.getPath());
            throw new StoreNewFileWithRedirectException(REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES);
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

    public String createAndProcessTask(Batch batch, RedirectAttributes redirectAttributes, Plan plan, List<File> dataFile, File mainDocFile) {
        long nanoTime = System.nanoTime();
        List<String> attachments = new ArrayList<>();
        String mainPoolCode = String.format("%d-%s-%d", plan.getId(), MAIN_DOCUMENT_POOL_CODE, nanoTime);
        String attachPoolCode = String.format("%d-%s-%d", plan.getId(), ATTACHMENTS_POOL_CODE, nanoTime);
        boolean isMainDocPresent = false;
        try {
            for (File file : dataFile) {
                String originFilename = file.getName();
                if (EXCLUDE_EXT.contains(StrUtils.getExtension(originFilename))) {
                    continue;
                }
                String name = StrUtils.getName(originFilename);
                String ext = StrUtils.getExtension(originFilename);
                final String code = StringUtils.replaceEach(name, new String[] {" "}, new String[] {"_"} ) + '-' + nanoTime + ext;

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
        } catch (StoreNewFileException e) {
            log.error("Error", e);
            redirectAttributes.addFlashAttribute("errorMessage", "#990.26 An error while saving data to file, " + e.toString());
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        if (!isMainDocPresent) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.28 main document wasn't found" );
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        final String paramYaml = asInputResourceParams(mainPoolCode, attachPoolCode, attachments);
        PlanApiData.TaskProducingResultComplex producingResult = planService.createWorkbook(plan.getId(), paramYaml);
        if (producingResult.planProducingStatus!= EnumsApi.PlanProducingStatus.OK) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.42 Error creating workbook: " + producingResult.planProducingStatus);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
        BatchWorkbook bfi = new BatchWorkbook();
        bfi.batchId=batch.id;
        bfi.workbookId=producingResult.workbook.getId();
        batchWorkbookRepository.save(bfi);

        // ugly work-around on StaleObjectStateException
        Long planId = plan.getId();
        plan = planCache.findById(planId);
        if (plan == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.49 plan wasn't found, planId: " + planId);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        // validate the plan + the workbook
        PlanApiData.PlanValidation planValidation = planService.validateInternal(plan);
        if (planValidation.status != EnumsApi.PlanValidateStatus.OK ) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.55 validation of plan was failed, status: " + planValidation.status);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        PlanApiData.TaskProducingResultComplex countTasks = planService.produceTasks(false, plan, producingResult.workbook);
        if (countTasks.planProducingStatus != EnumsApi.PlanProducingStatus.OK) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.60 validation of plan was failed, status: " + countTasks.planValidateStatus);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        if (globals.maxTasksPerPlan < countTasks.numberOfTasks) {
            planService.changeValidStatus(producingResult.workbook, false);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "#990.67 number of tasks for this workbook exceeded the allowed maximum number. Workbook was created but its status is 'not valid'. " +
                            "Allowed maximum number of tasks: " + globals.maxTasksPerPlan+", tasks in this workbook:  " + countTasks.numberOfTasks);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
        planService.changeValidStatus(producingResult.workbook, true);

        // start producing new tasks
        OperationStatusRest operationStatus = planService.workbookTargetExecState(producingResult.workbook.getId(), EnumsApi.WorkbookExecState.PRODUCING);

        if (operationStatus.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatus.errorMessages);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
        planService.createAllTasks();
        operationStatus = planService.workbookTargetExecState(producingResult.workbook.getId(), EnumsApi.WorkbookExecState.STARTED);

        if (operationStatus.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatus.errorMessages);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
        return null;
    }

    @SuppressWarnings("Duplicates")
    @GetMapping("/process-resource-delete/{planId}/{batchId}")
    public String processResourceDelete(Model model, @PathVariable Long planId, @PathVariable Long batchId, final RedirectAttributes redirectAttributes) {

        if (true) {
            redirectAttributes.addFlashAttribute("errorMessage", "Can't delete batch, not implemented yet.");
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        long workbookId = -1L;
        PlanApiData.WorkbookResult result = planService.getWorkbookExtended(workbookId);
        if (result.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.errorMessages);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
        model.addAttribute("result", result);
        return "pilot/process-resource/process-resource-delete";
    }

    @PostMapping("/process-resource-delete-commit")
    public String processResourceDeleteCommit(Long planId, Long workbookId, final RedirectAttributes redirectAttributes) {
        PlanApiData.WorkbookResult result = planService.getWorkbookExtended(workbookId);
        if (result.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.isErrorMessages());
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        Workbook wb = workbookRepository.findById(workbookId).orElse(null);
        if (wb==null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "#990.77 Workbook wasn't found, batchId: " + workbookId );
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
        planService.deleteWorkbook(workbookId, wb.getPlanId());
        return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
    }

    @GetMapping(value= "/process-resource-download-result/{batchId}/{fileName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public HttpEntity<AbstractResource> downloadProcessingResult(
            HttpServletResponse response, @PathVariable("batchId") Long batchId,
            @SuppressWarnings("unused") @PathVariable("fileName") String fileName/*, final RedirectAttributes redirectAttributes*/) throws IOException {
        log.info("#990.105 Start downloadProcessingResult(), batchId: {}", batchId);
        Batch batch = batchRepository.findById(batchId).orElse(null);
        if (batch==null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            log.info("#990.109 Batch wasn't found, batchId: {}", batchId);
            return null;
        }

        List<BatchWorkbook> bfis = batchWorkbookRepository.findAllByBatchId(batch.id);
        String status = "";
        File resultDir = DirUtils.createTempDir("prepare-file-processing-result-");
        File zipDir = new File(resultDir, "zip");

        for (BatchWorkbook bfi : bfis) {
            Workbook wb = workbookRepository.findById(bfi.workbookId).orElse(null);
            if (wb==null) {
                String msg = "#990.114 Batch #" + batch.id + " contains broken workbookId - #" + bfi.workbookId;
                status += (msg + '\n');
                log.warn(msg);
                continue;
            }
            Plan plan = planCache.findById(wb.getPlanId());
            if (plan==null) {
                String msg = "#990.119 Batch #" + batch.id + " contains broken planId - #" + plan.getId();
                status += (msg + '\n');
                log.warn(msg);
                continue;
            }

            String mainDocumentPoolCode = getMainDocumentPoolCode(wb.getInputResourceParam());

            final String fullMainDocument = getMainDocumentForPoolCode(mainDocumentPoolCode);
            if (fullMainDocument==null) {
                String msg = "#990.123, "+mainDocumentPoolCode+", Can't determine actual file name of main document, " +
                        "batchId: " + batch.id + ", workbookId: " + bfi.workbookId;
                log.warn(msg);
                status += (msg + '\n');
                continue;
            }
            PlanApiData.PlanYaml planYaml = planYamlUtils.toPlanYaml(plan.getParams());
            Meta meta = planYaml.getMeta(Consts.RESULT_FILE_EXTENSION);

            String mainDocument = StrUtils.getName(fullMainDocument) +
                    (meta!=null && StringUtils.isNotBlank(meta.getValue()) ? meta.getValue() : ".xml");

            Integer taskOrder = taskRepository.findMaxConcreteOrder(wb.getId());
            if (taskOrder==null) {
                String msg = "#990.128, "+mainDocument+", Tasks weren't created correctly for this batch, need to re-upload documents, " +
                        "batchId: " + batch.id + ", workbookId: " + bfi.workbookId;
                log.warn(msg);
                status += (msg + '\n');
                continue;
            }
            List<Task> tasks = taskRepository.findAnyWithConcreteOrder(wb.getId(), taskOrder);
            if (tasks.isEmpty()) {
                String msg = "#990.133, "+mainDocument+", Can't find any task for batchId: " + batchId;
                log.info(msg);
                status += (msg + '\n');
                continue;
            }
            if (tasks.size()>1) {
                String msg = "#990.137, "+mainDocument+", Can't download file because there are more than one task, " +
                        "batchId: "+batch.id+", workbookId: " + wb.getId();
                log.info(msg);
                status += (msg + '\n');
                continue;
            }
            final Task task = tasks.get(0);
            EnumsApi.TaskExecState execState = EnumsApi.TaskExecState.from(task.getExecState());
            switch (execState) {
                case NONE:
                case IN_PROGRESS:
                    status += ("#990.142, "+mainDocument+", Task hasn't completed yet, status: " + EnumsApi.TaskExecState.from(task.getExecState()) +
                            ", batchId:" + batch.id + ", workbookId: " + wb.getId() +", " +
                            "taskId: " + task.getId() + '\n');
                    continue;
                case ERROR:
                    SnippetApiData.SnippetExec snippetExec = SnippetExecUtils.to(task.getSnippetExecResults());
                    status += ("#990.149, "+mainDocument+", Task was completed with error, batchId:" + batch.id + ", workbookId: " + wb.getId() +", " +
                            "taskId: " + task.getId() + "\n" +
                            "isOk: " + snippetExec.exec.isOk + "\n" +
                            "exitCode: " + snippetExec.exec.exitCode + "\n" +
                            "console:\n" + (StringUtils.isNotBlank(snippetExec.exec.console) ? snippetExec.exec.console : "<output to console is blank>")+ "\n\n");
                    continue;
            }

            final TaskApiData.TaskParamYaml taskParamYaml = TaskParamYamlUtils.toTaskYaml(task.getParams());

            if (wb.getExecState()!= EnumsApi.WorkbookExecState.FINISHED.code) {
                status += ("#990.155, "+mainDocument+", Task hasn't completed yet, " +
                        "batchId:" + batch.id + ", workbookId: " + wb.getId() +", " +
                        "taskId: " + task.getId() + '\n');
                continue;
            }

            File mainDocFile = new File(zipDir, mainDocument);

            try {
                binaryDataService.storeToFile(taskParamYaml.outputResourceCode, mainDocFile);
            } catch (BinaryDataNotFoundException e) {
                String msg = "#990.161 Error store data to temp file, data doesn't exist in db, code " + taskParamYaml.outputResourceCode +
                        ", file: " + mainDocFile.getPath();
                log.error(msg);
                status += (msg + '\n');
                continue;
            }

            String msg = "#990.167 status - Ok, doc: "+mainDocFile.getName()+", batchId: " + batch.id + ", workbookId: " + bfi.workbookId;
            status += (msg + '\n');

        }
        File statusFile = new File(zipDir, "status.txt");
        FileUtils.write(statusFile, status, StandardCharsets.UTF_8);
        File zipFile = new File(resultDir, RESULT_ZIP);
        ZipUtils.createZip(zipDir, zipFile);


        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        httpHeaders.setContentDispositionFormData("attachment", RESULT_ZIP);
        return new HttpEntity<>(new FileSystemResource(zipFile), getHeader(httpHeaders, zipFile.length()));
    }

    private String getMainDocumentForPoolCode(String mainDocumentPoolCode) {
        List<BinaryData> datas = binaryDataService.getByPoolCodeAndType(mainDocumentPoolCode, EnumsApi.BinaryDataType.DATA);

        if (datas==null || datas.isEmpty()) {
            return null;
        }
        if (datas.size()!=1) {
            log.error("#990.15 There are more than one main document codes for pool code {}, codes: {}", mainDocumentPoolCode, datas);
        }
        final BinaryData data = datas.get(0);
        final String filename = data.getFilename();
        if (StringUtils.isBlank(filename)) {
            log.error("#990.15 Filename is blank for data {}", data);
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
