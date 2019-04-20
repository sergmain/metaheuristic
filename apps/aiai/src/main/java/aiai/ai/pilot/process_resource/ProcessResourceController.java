/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.pilot.process_resource;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.exceptions.BinaryDataNotFoundException;
import aiai.ai.exceptions.StoreNewFileException;
import aiai.ai.exceptions.StoreNewFileWithRedirectException;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.api.v1.EnumsApi;
import aiai.api.v1.launchpad.Task;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.data.FlowData;
import aiai.ai.launchpad.data.OperationStatusRest;
import aiai.ai.launchpad.flow.FlowCache;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.launchpad_resource.ResourceService;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.FlowRepository;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.pilot.beans.Batch;
import aiai.ai.pilot.beans.BatchFlowInstance;
import aiai.ai.utils.ControllerUtils;
import aiai.ai.utils.StrUtils;
import aiai.ai.yaml.input_resource_param.InputResourceParam;
import aiai.ai.yaml.input_resource_param.InputResourceParamUtils;
import aiai.ai.yaml.snippet_exec.SnippetExec;
import aiai.ai.yaml.snippet_exec.SnippetExecUtils;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import aiai.apps.commons.utils.DirUtils;
import aiai.apps.commons.utils.ZipUtils;
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessResourceItem {
        public Batch batch;
        public Flow flow;
        public String execStateStr;
        public boolean finished;
    }

    @Data
    public static class ProcessResourceResult {
        public Page<ProcessResourceItem> items;
    }

    @Data
    public static class FlowListResult {
        public Iterable<Flow> items;
    }

    private final Globals globals;
    private final FlowInstanceRepository flowInstanceRepository;
    private final FlowRepository flowRepository;
    private final FlowCache flowCache;
    private final FlowService flowService;
    private final ResourceService resourceService;
    private final TaskRepository taskRepository;
    private final BatchRepository batchRepository;
    private final BatchFlowInstanceRepository batchFlowInstanceRepository;
    private final BinaryDataService binaryDataService;

    public ProcessResourceController(Globals globals, FlowInstanceRepository flowInstanceRepository, FlowRepository flowRepository, FlowCache flowCache, FlowService flowService, ResourceService resourceService, TaskRepository taskRepository, BatchRepository batchRepository, BatchFlowInstanceRepository batchFlowInstanceRepository, BinaryDataService binaryDataService) {
        this.globals = globals;
        this.flowInstanceRepository = flowInstanceRepository;
        this.flowRepository = flowRepository;
        this.flowCache = flowCache;
        this.flowService = flowService;
        this.resourceService = resourceService;
        this.taskRepository = taskRepository;
        this.batchRepository = batchRepository;
        this.batchFlowInstanceRepository = batchFlowInstanceRepository;
        this.binaryDataService = binaryDataService;
    }

    @GetMapping("/process-resources")
    public String flows(@ModelAttribute(name = "result") ProcessResourceResult result, @PageableDefault(size = 5) Pageable pageable,
                        @ModelAttribute("errorMessage") final String errorMessage,
                        @ModelAttribute("infoMessages") final String infoMessages ) {
        prepareProcessResourcesResult(result, pageable);
        return "pilot/process-resource/process-resources";
    }

    @PostMapping("/process-resources-part")
    public String flowInstancesPart(@ModelAttribute(name = "result") ProcessResourceResult result, @PageableDefault(size = 10) Pageable pageable) {
        prepareProcessResourcesResult(result, pageable);
        return "pilot/process-resource/process-resources :: table";
    }

    private void prepareProcessResourcesResult(ProcessResourceResult result, Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(100, pageable);
        Page<Batch> batches = batchRepository.findAllByOrderByCreatedOnDesc(pageable);

        List<ProcessResourceItem> items = new ArrayList<>();
        long total = batches.getTotalElements();
        for (Batch batch : batches) {
            Flow flow = flowCache.findById(batch.getFlowId());
            if (flow==null) {
                log.warn("#990.01 Found batch with wrong flowId. flowId: {}", batch.getFlowId());
                total--;
                continue;
            }
            List<BatchFlowInstance> bfis = batchFlowInstanceRepository.findAllByBatchId(batch.id);
            boolean isFinished = true;
            for (BatchFlowInstance bfi : bfis) {
                FlowInstance fi  =flowInstanceRepository.findById(bfi.flowInstanceId).orElse(null);
                if (fi==null) {
                    String msg = "#990.03 Batch #" + batch.id + " contains broken flowInstanceId - #" + bfi.flowInstanceId;
                    log.warn(msg);
                    continue;
                }
                if (fi.execState != Enums.FlowInstanceExecState.ERROR.code &&
                        fi.execState != Enums.FlowInstanceExecState.FINISHED.code) {
                    isFinished = false;
                }
            }
            String execStateStr = isFinished ? "FINISHED" : "IN PROGRESS";
            items.add( new ProcessResourceItem(batch, flow, execStateStr, isFinished));
        }
        result.items = new PageImpl<>(items, pageable, total);

        //noinspection unused
        int i=0;
    }

    @GetMapping(value = "/process-resource-add")
    public String flowInstanceAdd(@ModelAttribute("result") FlowListResult result) {
        result.items = flowRepository.findAll();
        return "pilot/process-resource/process-resource-add";
    }

    @PostMapping(value = "/process-resource-upload-from-file")
    public String uploadFile(final Model model, final MultipartFile file, Long flowId, final RedirectAttributes redirectAttributes) {

        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.10 name of uploaded file is null");
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
        originFilename = originFilename.toLowerCase();
        if (!StringUtils.endsWithAny(originFilename, ".xml", ".zip", ".doc", ".docx")) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.20 only '.xml' and .zip files are supported, filename: " + originFilename);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
        Flow flow = flowCache.findById(flowId);
        if (flow == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.31 flow wasn't found, flowId: " + flowId);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        // validate the flow
        FlowData.FlowValidation flowValidation = flowService.validateInternal(flow);
        if (flowValidation.status != EnumsApi.FlowValidateStatus.OK ) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.37 validation of flow was failed, status: " + flowValidation.status);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        try {
            File tempDir = DirUtils.createTempDir("document-upload-");
            if (tempDir==null || tempDir.isFile()) {
                redirectAttributes.addFlashAttribute("errorMessage", "#990.24 can't create temporary directory in " + System.getProperty("java.io.tmpdir"));
                return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
            }

            String ext = StrUtils.getExtension(originFilename);

            final File dataFile = new File(tempDir, "document" + ext );
            log.debug("Start storing an uploaded document to disk");
            try(OutputStream os = new FileOutputStream(dataFile)) {
                IOUtils.copy(file.getInputStream(), os, 32000);
            }

            Batch batch = new Batch(flow.id);
            batchRepository.save(batch);

            log.info("The file {} was successfully uploaded", originFilename);

            if (originFilename.endsWith(".zip")) {
                log.debug("Start unzipping archive");
                ZipUtils.unzipFolder(dataFile, tempDir);
                log.debug("Start loading file data to db");
                loadFilesFromDirAfterZip(batch, tempDir, redirectAttributes, flow);
            }
            else {
                log.debug("Start loading file data to db");
                loadFilesFromDir(batch, tempDir, redirectAttributes, flow);
            }
        }
        catch(StoreNewFileWithRedirectException e) {
            return e.redirect;
        }
        catch (Throwable th) {
            log.error("Error", th);
            redirectAttributes.addFlashAttribute("errorMessage", "#990.73 can't load document, Error: " + th.toString());
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
    }

    private void loadFilesFromDir(Batch batch, File srcDir, RedirectAttributes redirectAttributes, Flow flow) throws IOException {
        Files.list(srcDir.toPath())
                .filter( o -> {
                    File f = o.toFile();
                    return f.isFile() && !EXCLUDE_EXT.contains(StrUtils.getExtension(f.getName()));
                })
                .forEach(dataFile -> {
                    File file = dataFile.toFile();
                    String redirectUrl1 = createAndProcessTask(batch, redirectAttributes, flow, Collections.singletonList(file), file);
                    if (redirectUrl1 != null) {
                        throw new StoreNewFileWithRedirectException(redirectUrl1);
                    }
                });
    }

    private void loadFilesFromDirAfterZip(Batch batch, File srcDir, RedirectAttributes redirectAttributes, Flow flow) throws IOException {

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

                            String redirectUrl1 = createAndProcessTask(batch, redirectAttributes, flow, files, mainDocFile);
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
                        String redirectUrl1 = createAndProcessTask(batch, redirectAttributes, flow, Collections.singletonList(file), file);
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

    public String createAndProcessTask(Batch batch, RedirectAttributes redirectAttributes, Flow flow, List<File> dataFile, File mainDocFile) {
        long nanoTime = System.nanoTime();
        List<String> attachments = new ArrayList<>();
        String mainPoolCode = String.format("%d-%s-%d", flow.id, MAIN_DOCUMENT_POOL_CODE, nanoTime);
        String attachPoolCode = String.format("%d-%s-%d", flow.id, ATTACHMENTS_POOL_CODE, nanoTime);
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

                resourceService.storeInitialResource(originFilename, file, code, poolCode, originFilename);
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
        FlowService.TaskProducingResult producingResult = flowService.createFlowInstance(flow, paramYaml);
        if (producingResult.flowProducingStatus!= EnumsApi.FlowProducingStatus.OK) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.42 Error creating flowInstance: " + producingResult.flowProducingStatus);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
        BatchFlowInstance bfi = new BatchFlowInstance();
        bfi.batchId=batch.id;
        bfi.flowInstanceId=producingResult.flowInstance.id;
        batchFlowInstanceRepository.save(bfi);

        // ugly work-around on StaleObjectStateException
        Long flowId = flow.getId();
        flow = flowCache.findById(flowId);
        if (flow == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.49 flow wasn't found, flowId: " + flowId);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        // validate the flow + the flow instance
        FlowData.FlowValidation flowValidation = flowService.validateInternal(flow);
        if (flowValidation.status != EnumsApi.FlowValidateStatus.OK ) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.55 validation of flow was failed, status: " + flowValidation.status);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        FlowService.TaskProducingResult countTasks = new FlowService.TaskProducingResult();
        flowService.produceTasks(false, countTasks, flow, producingResult.flowInstance);
        if (countTasks.flowProducingStatus != EnumsApi.FlowProducingStatus.OK) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.60 validation of flow was failed, status: " + countTasks.flowValidateStatus);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        if (globals.maxTasksPerFlow < countTasks.numberOfTasks) {
            flowService.changeValidStatus(producingResult.flowInstance, false);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "#990.67 number of tasks for this flow instance exceeded the allowed maximum number. Flow instance was created but its status is 'not valid'. " +
                            "Allowed maximum number of tasks: " + globals.maxTasksPerFlow+", tasks in this flow instance:  " + countTasks.numberOfTasks);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
        flowService.changeValidStatus(producingResult.flowInstance, true);

        // start producing new tasks
        OperationStatusRest operationStatus = flowService.flowInstanceTargetExecState(
                flow.getId(), producingResult.flowInstance.getId(), Enums.FlowInstanceExecState.PRODUCING);

        if (operationStatus.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatus.errorMessages);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
        flowService.createAllTasks();
        operationStatus = flowService.flowInstanceTargetExecState(
                flow.getId(), producingResult.flowInstance.getId(), Enums.FlowInstanceExecState.STARTED);

        if (operationStatus.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatus.errorMessages);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
        return null;
    }

    @SuppressWarnings("Duplicates")
    @GetMapping("/process-resource-delete/{flowId}/{batchId}")
    public String processResourceDelete(Model model, @PathVariable Long flowId, @PathVariable Long batchId, final RedirectAttributes redirectAttributes) {

        if (true) throw new IllegalStateException("Not implemented yet");
        long flowInstanceId = -1L;
        FlowData.FlowInstanceResult result = flowService.prepareModel(flowId, flowInstanceId);
        if (result.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.errorMessages);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
        model.addAttribute("result", result);
        return "pilot/process-resource/process-resource-delete";
    }

    @SuppressWarnings("Duplicates")
    @PostMapping("/process-resource-delete-commit")
    public String processResourceDeleteCommit(Long flowId, Long flowInstanceId, final RedirectAttributes redirectAttributes) {
        FlowData.FlowInstanceResult result = flowService.prepareModel(flowId, flowInstanceId);
        if (result.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.isErrorMessages());
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        FlowInstance fi = flowInstanceRepository.findById(flowInstanceId).orElse(null);
        if (fi==null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "#990.77 FlowInstance wasn't found, batchId: " + flowInstanceId );
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
        flowService.deleteFlowInstance(flowInstanceId, fi.flowId);
        return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
    }

    @GetMapping(value= "/process-resource-download-result/{batchId}/{fileName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public HttpEntity<AbstractResource> downloadProcessingResult(
            HttpServletResponse response, @PathVariable("batchId") Long batchId,
            @SuppressWarnings("unused") @PathVariable("fileName") String fileName/*, final RedirectAttributes redirectAttributes*/) throws IOException {
        log.info("#990.82 Start downloadProcessingResult(), batchId: {}", batchId);
        Batch batch = batchRepository.findById(batchId).orElse(null);
        if (batch==null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            log.info("#990.84 Batch wasn't found, batchId: {}", batchId);
            return null;
        }

        List<BatchFlowInstance> bfis = batchFlowInstanceRepository.findAllByBatchId(batch.id);
        String status = "";
        File resultDir = DirUtils.createTempDir("prepare-doc-processing-result-");
        File zipDir = new File(resultDir, "zip");

        for (BatchFlowInstance bfi : bfis) {
            FlowInstance fi  =flowInstanceRepository.findById(bfi.flowInstanceId).orElse(null);
            if (fi==null) {
                String msg = "#990.80 Batch #" + batch.id + " contains broken flowInstanceId - #" + bfi.flowInstanceId;
                status += (msg + '\n');
                log.warn(msg);
                continue;
            }
            String mainDocument = getMainDocument(fi.getInputResourceParam());

            Integer taskOrder = taskRepository.findMaxConcreteOrder(fi.id);
            if (taskOrder==null) {
                String msg = "#990.82, "+mainDocument+", Can't get taskOrder, " +
                        "batchId: " + batch.id + ", flowInstanceId: " + bfi.flowInstanceId;
                log.warn(msg);
                status += (msg + '\n');
                continue;
            }
            List<Task> tasks = taskRepository.findAnyWithConcreteOrder(fi.getId(), taskOrder);
            if (tasks.isEmpty()) {
                String msg = "#990.88, "+mainDocument+", Can't find any task for batchId: " + batchId;
                log.info(msg);
                status += (msg + '\n');
                continue;
            }
            if (tasks.size()>1) {
                String msg = "#990.90, "+mainDocument+", Can't download file because there are more than one task, " +
                        "batchId: "+batch.id+", flowInstanceId: " + fi.id;
                log.info(msg);
                status += (msg + '\n');
                continue;
            }
            final Task task = tasks.get(0);
            Enums.TaskExecState execState = Enums.TaskExecState.from(task.getExecState());
            switch (execState) {
                case NONE:
                case IN_PROGRESS:
                    status += ("#990.50, "+mainDocument+", Task hasn't completed yet, status: " +Enums.TaskExecState.from(task.getExecState()) +
                            ", batchId:" + batch.id + ", flowInstanceId: " + fi.id +", " +
                            "taskId: " + task.getId() + '\n');
                    continue;
                case ERROR:
                    SnippetExec snippetExec = SnippetExecUtils.to(task.getSnippetExecResults());
                    status += ("#990.52, "+mainDocument+", Task was completed with error, batchId:" + batch.id + ", flowInstanceId: " + fi.id +", " +
                            "taskId: " + task.getId() + "\n" +
                            "isOk: " + snippetExec.exec.isOk + "\n" +
                            "exitCode: " + snippetExec.exec.exitCode + "\n" +
                            "console:\n" + (StringUtils.isNotBlank(snippetExec.exec.console) ? snippetExec.exec.console : "<output to console is blank>")+ "\n\n");
                    continue;
            }

            final TaskParamYaml taskParamYaml = TaskParamYamlUtils.toTaskYaml(task.getParams());

            if (fi.getExecState()!= Enums.FlowInstanceExecState.FINISHED.code) {
                status += ("#990.95, "+mainDocument+", Task hasn't completed yet, " +
                        "batchId:" + batch.id + ", flowInstanceId: " + fi.id +", " +
                        "taskId: " + task.getId() + '\n');
                continue;
            }

            File mainDocFile = new File(zipDir, mainDocument);

            try {
                binaryDataService.storeToFile(taskParamYaml.outputResourceCode, mainDocFile);
            } catch (BinaryDataNotFoundException e) {
                String msg = "#990.06 Error store data to temp file, data doesn't exist in db, code " + taskParamYaml.outputResourceCode +
                        ", file: " + mainDocFile.getPath();
                log.error(msg);
                status += (msg + '\n');
                continue;
            }

            String msg = "#990.08 status - Ok, doc: "+mainDocFile.getName()+", batchId: " + batch.id + ", flowInstanceId: " + bfi.flowInstanceId;
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

    private static HttpHeaders getHeader(HttpHeaders httpHeaders, long length) {
        HttpHeaders header = httpHeaders != null ? httpHeaders : new HttpHeaders();
        header.setContentLength(length);
        header.setCacheControl("max-age=0");
        header.setExpires(0);
        header.setPragma("no-cache");

        return header;
    }

    private static String getResultFileName(String inputResourceParams) {
        InputResourceParam resourceParams = InputResourceParamUtils.to(inputResourceParams);
        List<String> codes = resourceParams.getAllPoolCodes();
        if (codes.isEmpty()) {
            throw new IllegalStateException("#990.92 Pool codes not found.");
        }
        return codes.size() == 1 ? codes.get(0) : "result-file-" + System.nanoTime();
    }

    private static String getMainDocument(String inputResourceParams) {
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
