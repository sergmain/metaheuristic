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
import aiai.ai.exceptions.StoreNewFileException;
import aiai.ai.exceptions.StoreNewFileWithRedirectException;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.data.FlowData;
import aiai.ai.launchpad.data.OperationStatusRest;
import aiai.ai.launchpad.flow.FlowCache;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.launchpad_resource.ResourceService;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.FlowRepository;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.launchpad.server.ServerService;
import aiai.ai.pilot.beans.Batch;
import aiai.ai.pilot.beans.BatchFlowInstance;
import aiai.ai.utils.ControllerUtils;
import aiai.ai.utils.StrUtils;
import aiai.ai.yaml.input_resource_param.InputResourceParam;
import aiai.ai.yaml.input_resource_param.InputResourceParamUtils;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import aiai.apps.commons.utils.DirUtils;
import aiai.apps.commons.utils.ZipUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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
import java.nio.file.Files;
import java.util.*;

@Controller
@RequestMapping("/pilot/process-resource")
@Slf4j
@Profile("launchpad")
public class ProcessResourceController {

    private static final String REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES = "redirect:/pilot/process-resource/process-resources";
    private static final String SINGLE_CODE = "  - ";
    private static final String MAIN_DOCUMENT_POOL_CODE = "mainDocument";
    private static final String ATTACHMENTS_POOL_CODE = "attachments";

    private static Set<String> EXCLUDE_EXT = Set.of(".zip", ".yaml");

    private static final String CONFIG_FILE = "config.yaml";

    @Data
    public static class ProcessResourceResult {
        public Slice<Batch> resources;
        public Map<Long, Flow> flows = new HashMap<>();
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
    private final ServerService serverService;
    private final TaskRepository taskRepository;
    private final BatchRepository batchRepository;
    private final BatchFlowInstanceRepository batchFlowInstanceRepository;

    public ProcessResourceController(Globals globals, FlowInstanceRepository flowInstanceRepository, FlowRepository flowRepository, FlowCache flowCache, FlowService flowService, ResourceService resourceService, ServerService serverService, TaskRepository taskRepository, BatchRepository batchRepository, BatchFlowInstanceRepository batchFlowInstanceRepository) {
        this.globals = globals;
        this.flowInstanceRepository = flowInstanceRepository;
        this.flowRepository = flowRepository;
        this.flowCache = flowCache;
        this.flowService = flowService;
        this.resourceService = resourceService;
        this.serverService = serverService;
        this.taskRepository = taskRepository;
        this.batchRepository = batchRepository;
        this.batchFlowInstanceRepository = batchFlowInstanceRepository;
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
        result.resources = batchRepository.findAllByOrderByCreatedOnDesc(pageable);

        for (Batch batch : result.resources) {
            Flow flow = flowCache.findById(batch.getFlowId());
            if (flow==null) {
                log.warn("#990.01 Found batch with wrong flowId. flowId: {}", batch.getFlowId());
                continue;
            }
            result.flows.put(batch.getId(), flow);
        }
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
        if (flowValidation.status != Enums.FlowValidateStatus.OK ) {
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

    public static String asInputResourceParams(String mainDocCode, List<String> attachmentCodes) {
        String yaml = "poolCodes:\n  " + MAIN_DOCUMENT_POOL_CODE + ":\n" +
                SINGLE_CODE + mainDocCode;

        if (attachmentCodes.isEmpty()) {
            return yaml;
        }
        yaml += "\n  " + ATTACHMENTS_POOL_CODE + ":\n";
        for (String code : attachmentCodes) {
            //noinspection StringConcatenationInLoop
            yaml += (SINGLE_CODE + code + '\n');
        }
        return yaml;
    }

    public String createAndProcessTask(Batch batch, RedirectAttributes redirectAttributes, Flow flow, List<File> dataFile, File mainDocFile) {
        long nanoTime = System.nanoTime();
        List<String> attachments = new ArrayList<>();
        String mainDocCode = null;
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
                    poolCode = MAIN_DOCUMENT_POOL_CODE;
                    mainDocCode = code;
                }
                else {
                    poolCode = ATTACHMENTS_POOL_CODE;
                    attachments.add(code);
                }

                resourceService.storeInitialResource(originFilename, file, code, poolCode, originFilename);
            }
        } catch (StoreNewFileException e) {
            log.error("Error", e);
            redirectAttributes.addFlashAttribute("errorMessage", "#990.26 An error while saving data to file, " + e.toString());
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        if (mainDocCode==null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.28 main document wasn't found" );
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        final String paramYaml = asInputResourceParams(mainDocCode, attachments);
        FlowService.TaskProducingResult producingResult = flowService.createFlowInstance(flow, paramYaml);
        if (producingResult.flowProducingStatus!= Enums.FlowProducingStatus.OK) {
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
        if (flowValidation.status != Enums.FlowValidateStatus.OK ) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.55 validation of flow was failed, status: " + flowValidation.status);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        FlowService.TaskProducingResult countTasks = new FlowService.TaskProducingResult();
        flowService.produceTasks(false, countTasks, flow, producingResult.flowInstance);
        if (countTasks.flowProducingStatus != Enums.FlowProducingStatus.OK) {
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
    public String processResourceDelete(Model model, @PathVariable Long flowId, @PathVariable Long flowInstanceId, final RedirectAttributes redirectAttributes) {
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
        flowService.deleteFlowInstance(flowInstanceId, fi);
        return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
    }

    @GetMapping(value= "/process-resource-download-result/{batchId}/{fileName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public HttpEntity<AbstractResource> downloadFile(
            HttpServletResponse response, @PathVariable("batchId") Long batchId,
            @SuppressWarnings("unused") @PathVariable("fileName") String fileName/*, final RedirectAttributes redirectAttributes*/) throws IOException {
        log.info("#990.82 Start downloadFile(), batchId: {}", batchId);
        Batch batch = batchRepository.findById(batchId).orElse(null);
        if (batch==null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            log.info("#990.84 Batch wasn't found, batchId: {}", batchId);
            return null;
        }

/*
        if (batch.getExecState()!= Enums.FlowInstanceExecState.FINISHED.code) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            log.info("#990.84 File can't be downloaded because flowInstance doesn't have execState==Enums.FlowInstanceExecState.FINISHED, actual {}", batch.getExecState());
            return null;
        }
        Integer taskOrder = taskRepository.findMaxConcreteOrder(batch.getId());
        if (taskOrder==null) {
            log.info("#990.86 Can't calculate the max task order, batchId: {}", batchId);
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return null;
        }
        List<Task> tasks = taskRepository.findWithConcreteOrder(batch.getId(), taskOrder);
        if (tasks.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            log.info("#990.88 Can't find any task for batchId {}}", batchId);
            return null;
        }
        if (tasks.size()>1) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            log.info("#990.90 Can't download file because there are more than one task for batchId {}}", batchId);
//            redirectAttributes.addFlashAttribute("errorMessage", "#990.10 name of uploaded file is null");
//            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;

            return null;
        }
        final Task task = tasks.get(0);
        final TaskParamYaml taskParamYaml = TaskParamYamlUtils.toTaskYaml(task.getParams());
*/

        HttpHeaders httpHeaders = new HttpHeaders();
//        httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
//                getResultFileName(batch.getInputResourceParam())+"-result.xml"
//        httpHeaders.setContentDispositionFormData("attachment","result.zip");
//        return serverService.deliverResource(Enums.BinaryDataType.DATA, taskParamYaml.outputResourceCode, httpHeaders);

        httpHeaders.setContentType(MediaType.APPLICATION_XML);
        httpHeaders.setContentDispositionFormData("attachment","result.xml");

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Aaaa>aaa</Aaaa>";
        byte[] bytes = xml.getBytes();
        return new HttpEntity<>(new ByteArrayResource(bytes), getHeader(httpHeaders, bytes.length));
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
        List<String> codes = resourceParams.getAllCodes();
        if (codes.isEmpty()) {
            throw new IllegalStateException("#990.92 Pool codes not found.");
        }
        return codes.size() == 1 ? codes.get(0) : "result-file-" + System.nanoTime();
    }

}
