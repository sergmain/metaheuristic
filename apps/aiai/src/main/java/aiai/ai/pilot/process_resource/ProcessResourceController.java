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
import aiai.ai.exceptions.StoreNewPartOfRawFileException;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.flow.FlowCache;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.FlowRepository;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.launchpad.resource.ResourceService;
import aiai.ai.launchpad.server.ServerService;
import aiai.ai.utils.ControllerUtils;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import aiai.apps.commons.utils.DirUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
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

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/pilot/process-resource")
@Slf4j
@Profile("launchpad")
public class ProcessResourceController {

    private static final String REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES = "redirect:/pilot/process-resource/process-resources";

    @Data
    public static class ProcessResourceResult {
        public Slice<FlowInstance> resources;
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
    private final TaskParamYamlUtils taskParamYamlUtils;

    public ProcessResourceController(Globals globals, FlowInstanceRepository flowInstanceRepository, FlowRepository flowRepository, FlowCache flowCache, FlowService flowService, ResourceService resourceService, ServerService serverService, TaskRepository taskRepository, TaskParamYamlUtils taskParamYamlUtils) {
        this.globals = globals;
        this.flowInstanceRepository = flowInstanceRepository;
        this.flowRepository = flowRepository;
        this.flowCache = flowCache;
        this.flowService = flowService;
        this.resourceService = resourceService;
        this.serverService = serverService;
        this.taskRepository = taskRepository;
        this.taskParamYamlUtils = taskParamYamlUtils;
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
        result.resources = flowInstanceRepository.findAllByOrderByExecStateDescCompletedOnDesc(pageable);

        for (FlowInstance flowInstance : result.resources) {
            Flow flow = flowCache.findById(flowInstance.getFlowId());
            if (flow==null) {
                log.warn("#990.01 Found flowInstance with wrong flowId. flowId: {}", flowInstance.getFlowId());
                continue;
            }
            result.flows.put(flowInstance.getId(), flow);
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
        int idx;
        if ((idx = originFilename.lastIndexOf('.')) == -1) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.15 The char '.' wasn't found, bad filename: " + originFilename);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
        String ext = originFilename.substring(idx).toLowerCase();
        if (!".xml".equals(ext)) {
            redirectAttributes.addFlashAttribute("errorMessage", "#990.20 only '.xml' files is supported, filename: " + originFilename);
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        try {
            File tempDir = DirUtils.createTempDir("document-upload-");
            if (tempDir==null || tempDir.isFile()) {
                redirectAttributes.addFlashAttribute("errorMessage", "#990.24 can't create temporary directory in " + System.getProperty("java.io.tmpdir"));
                return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
            }
            final File dataFile = new File(tempDir, "document.xml");
            log.debug("Start storing an uploaded document to disk");
            try(OutputStream os = new FileOutputStream(dataFile)) {
                IOUtils.copy(file.getInputStream(), os, 32000);
            }
            log.info("The file {} was successfully uploaded", originFilename);

            final String code = StringUtils.replaceEach(originFilename, new String[] {".", " "}, new String[] {"-", "_"} );
            final String resourcePoolCode = originFilename  + '-' + System.nanoTime();

            try {
                resourceService.storeInitialResource(originFilename, dataFile, code, resourcePoolCode, originFilename);
            } catch (StoreNewPartOfRawFileException e) {
                log.error("Error", e);
                redirectAttributes.addFlashAttribute("errorMessage", "#990.26 An error while saving data to file, " + e.toString());
                return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
            }
            Flow flow = flowCache.findById(flowId);
            if (flow == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "#990.31 flow wasn't found, flowId: " + flowId);
                return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
            }

            // validate the flow
            Enums.FlowValidateStatus validateStatus = flowService.validateInternal(model, flow);
            if (validateStatus != Enums.FlowValidateStatus.OK ) {
                redirectAttributes.addFlashAttribute("errorMessage", "#990.37 validation of flow was failed, status: " + validateStatus);
                return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
            }

            FlowService.TaskProducingResult producingResult = flowService.createFlowInstance(flow, resourcePoolCode);
            if (producingResult.flowProducingStatus!= Enums.FlowProducingStatus.OK) {
                redirectAttributes.addFlashAttribute("errorMessage", "#990.42 Error creating flowInstance: " + producingResult.flowProducingStatus);
                return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
            }

            // ugly work-around on StaleObjectStateException
            flow = flowCache.findById(flowId);
            if (flow == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "#990.49 flow wasn't found, flowId: " + flowId);
                return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
            }

            // validate the flow + the flow instance
            validateStatus = flowService.validateInternal(model, flow);
            if (validateStatus != Enums.FlowValidateStatus.OK ) {
                redirectAttributes.addFlashAttribute("errorMessage", "#990.55 validation of flow was failed, status: " + validateStatus);
                return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
            }

            FlowService.TaskProducingResult countTasks = new FlowService.TaskProducingResult();
            flowService.produce(false, countTasks, flow, producingResult.flowInstance);
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
            String redirectUrl1 = flowService.flowInstanceTargetExecState(
                    flowId, producingResult.flowInstance.getId(), model, redirectAttributes, Enums.FlowInstanceExecState.PRODUCING, REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES);

            if (redirectUrl1 != null) {
                return redirectUrl1;
            }
            flowService.createAllTasks();
            redirectUrl1 = flowService.flowInstanceTargetExecState(
                    flowId, producingResult.flowInstance.getId(), model, redirectAttributes, Enums.FlowInstanceExecState.STARTED, REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES);

            if (redirectUrl1 != null) {
                return redirectUrl1;
            }
        }
        catch (Exception e) {
            log.error("Error", e);
            redirectAttributes.addFlashAttribute("errorMessage", "#990.73 can't load document, Error: " + e.toString());
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }

        return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
    }

    @GetMapping("/process-resource-delete/{flowId}/{flowInstanceId}")
    public String processResourceDelete(@PathVariable Long flowId, @PathVariable Long flowInstanceId, Model model, final RedirectAttributes redirectAttributes) {
        String redirectUrl = flowService.prepareModel(flowId, flowInstanceId, model, redirectAttributes, REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES);
        if (redirectUrl != null) {
            return redirectUrl;
        }
        return "pilot/process-resource/process-resource-delete";
    }

    @PostMapping("/process-resource-delete-commit")
    public String processResourceDeleteCommit(Long flowId, Long flowInstanceId, Model model, final RedirectAttributes redirectAttributes) {
        String redirectUrl = flowService.prepareModel(flowId, flowInstanceId, model, redirectAttributes, REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES);
        if (redirectUrl != null) {
            return redirectUrl;
        }

        FlowInstance fi = flowInstanceRepository.findById(flowInstanceId).orElse(null);
        if (fi==null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "#560.77 FlowInstance wasn't found, flowInstanceId: " + flowInstanceId );
            return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
        }
        flowService.deleteFlowInstance(flowInstanceId, fi);
        return REDIRECT_PILOT_PROCESS_RESOURCE_PROCESS_RESOURCES;
    }

    @GetMapping(value="/process-resource-download-result/{flowInstanceId}/{fileName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public HttpEntity<AbstractResource> downloadFile(
            HttpServletResponse response, @PathVariable("flowInstanceId") Long flowInstanceId,
            @SuppressWarnings("unused") @PathVariable("fileName") String fileName) throws IOException {
        FlowInstance fi = flowInstanceRepository.findById(flowInstanceId).orElse(null);
        if (fi==null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

        if (fi.getExecState()!= Enums.FlowInstanceExecState.FINISHED.code) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return null;
        }
        List<Task> tasks = taskRepository.findWithConcreteOrder(fi.getId(), fi.getProducingOrder()-1);
        if (tasks.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        if (tasks.size()>1) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return null;
        }
        final Task task = tasks.get(0);
        final TaskParamYaml taskParamYaml = taskParamYamlUtils.toTaskYaml(task.getParams());

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_XML);
        httpHeaders.setContentDispositionFormData("attachment", fi.getInputResourcePoolCode()+"-result.xml" );

        return serverService.deliverResource(Enums.BinaryDataType.DATA, taskParamYaml.outputResourceCode, httpHeaders);
    }

}
