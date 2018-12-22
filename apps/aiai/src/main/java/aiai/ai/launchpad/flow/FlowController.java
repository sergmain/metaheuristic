package aiai.ai.launchpad.flow;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.repositories.*;
import aiai.ai.utils.ControllerUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/launchpad/flow")
@Slf4j
@Profile("launchpad")
public class FlowController {

    @Data
    public static class Result {
        public Slice<Flow> items;
    }

    @Data
    public static class FlowInstancesResult {
        public long currentFlowId;
        public Slice<FlowInstance> instances;
        public Map<Long, Flow> flows = new HashMap<>();
    }

    @Data
    @AllArgsConstructor
    public static class FlowInstanceResult {
        public FlowInstance flowInstance;
        public Flow flow;
    }

    @Data
    public static class FlowListResult {
        public Iterable<Flow> items;
        public long currentFlowId;
    }

    private final Globals globals;
    private final FlowCache flowCache;
    private final FlowService flowService;
    private final FlowRepository flowRepository;
    private final FlowInstanceRepository flowInstanceRepository;
    private final ExperimentTaskFeatureRepository taskExperimentFeatureRepository;
    private final FlowInstanceService flowInstanceService;
    private final ExperimentService experimentService;
    private final BinaryDataService binaryDataService;

    public FlowController(Globals globals, FlowCache flowCache, FlowService flowService, FlowRepository flowRepository1, FlowInstanceRepository flowInstanceRepository, ExperimentTaskFeatureRepository taskExperimentFeatureRepository, FlowInstanceService flowInstanceService, ExperimentService experimentService, BinaryDataService binaryDataService) {
        this.globals = globals;
        this.flowCache = flowCache;
        this.flowService = flowService;
        this.flowRepository = flowRepository1;
        this.flowInstanceRepository = flowInstanceRepository;
        this.taskExperimentFeatureRepository = taskExperimentFeatureRepository;
        this.flowInstanceService = flowInstanceService;
        this.experimentService = experimentService;
        this.binaryDataService = binaryDataService;
    }

    @GetMapping("/flows")
    public String flows(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable, @ModelAttribute("errorMessage") final String errorMessage) {
        pageable = ControllerUtils.fixPageSize(globals.flowRowsLimit, pageable);
        result.items = flowRepository.findAll(pageable);
        return "launchpad/flow/flows";
    }

    // for AJAX
    @PostMapping("/flows-part")
    public String flowsPart(@ModelAttribute Result result, @PageableDefault(size = 10) Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.flowRowsLimit, pageable);
        result.items = flowRepository.findAll(pageable);
        return "launchpad/flow/flows :: table";
    }

    @GetMapping(value = "/flow-add")
    public String add(@ModelAttribute("flow") Flow flow) {
        return "launchpad/flow/flow-add";
    }

    @GetMapping(value = "/flow-edit/{id}")
    public String edit(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        final Flow flow = flowCache.findById(id);
        if (flow == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#559.01 flow wasn't found, flowId: " + id);
            return "redirect:/launchpad/flow/flows";
        }
        model.addAttribute("flow", flow);
        return "launchpad/flow/flow-edit";
    }

    @GetMapping(value = "/flow-validate/{id}")
    public String validate(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        final Flow flow = flowCache.findById(id);
        if (flow == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.01 flow wasn't found, flowId: " + id);
            return "redirect:/launchpad/flow/flows";
        }
        model.addAttribute("flow", flow);

        Enums.FlowValidateStatus flowValidateStatus = flowService.validate(flow);
        flow.valid = flowValidateStatus == Enums.FlowValidateStatus.OK;
        flowCache.save(flow);
        if (flow.valid) {
            model.addAttribute("infoMessages", "Validation result: OK");
        }
        else {
            log.error("Validation error: {}", flowValidateStatus);
            model.addAttribute("errorMessage", "#561.01 Validation error: : " + flowValidateStatus);
        }
        model.addAttribute("flow", flow);
        return "launchpad/flow/flow-edit";
    }

    @PostMapping("/flow-add-commit")
    public String addFormCommit(Model model, Flow flow) {
        return processFlowCommit(model, flow, "launchpad/flow/flow-add", "redirect:/launchpad/flow/flows");
    }

    @PostMapping("/flow-edit-commit")
    public String editFormCommit(Model model, Flow flowModel, final RedirectAttributes redirectAttributes) {
        Flow flow = flowCache.findById(flowModel.getId());
        if (flow == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.10 flow wasn't found, flowId: " + flowModel.getId());
            return "redirect:/launchpad/flow/flows";
        }
        flow.setCode(flowModel.getCode());
        flow.setParams(flowModel.getParams());
        return processFlowCommit(model, flow,"launchpad/flow/flow-edit","redirect:/launchpad/flow/flow-edit/"+flow.getId());
    }

    private String processFlowCommit(Model model, Flow flow, String errorTarget, String normalTarget) {
        if (StringUtils.isBlank(flow.code)) {
            model.addAttribute("errorMessage", "#560.20 code of flow is empty");
            return errorTarget;
        }
        if (StringUtils.isBlank(flow.code)) {
            model.addAttribute("errorMessage", "#560.30 flow is empty");
            return errorTarget;
        }
        Flow f = flowRepository.findByCode(flow.code);
        if (f!=null) {
            model.addAttribute("errorMessage", "#560.33 flow with such code already exists, code: " + flow.code);
            return errorTarget;
        }
        flowCache.save(flow);
        return normalTarget;
    }

    @GetMapping("/flow-delete/{id}")
    public String delete(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        Flow flow = flowCache.findById(id);
        if (flow == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.40 flow wasn't found, id: "+id );
            return "redirect:/launchpad/flow/flows";
        }
        model.addAttribute("flow", flow);
        return "launchpad/flow/flow-delete";
    }

    @PostMapping("/flow-delete-commit")
    public String deleteCommit(Long id, final RedirectAttributes redirectAttributes) {
        Flow flow = flowCache.findById(id);
        if (flow == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.50 flow wasn't found, flowId: " + id);
            return "redirect:/launchpad/flow/flows";
        }
        flowCache.deleteById(id);
        return "redirect:/launchpad/flow/flows";
    }

    // ============= Flow instances =============

    @GetMapping("/flow-instances/{id}")
    public String flowInstances(@ModelAttribute(name = "result") FlowInstancesResult result, @PathVariable Long id, @PageableDefault(size = 5) Pageable pageable, @ModelAttribute("errorMessage") final String errorMessage) {
        prepareFlowInstanceResult(result, id, pageable);
        return "launchpad/flow/flow-instances";
    }

    // for AJAX
    @PostMapping("/flow-instances-part/{id}")
    public String flowInstancesPart(@ModelAttribute(name = "result") FlowInstancesResult result, @PathVariable Long id, @PageableDefault(size = 10) Pageable pageable) {
        prepareFlowInstanceResult(result, id, pageable);
        return "launchpad/flow/flow-instances :: table";
    }

    private void prepareFlowInstanceResult(FlowInstancesResult result, Long id, Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.flowInstanceRowsLimit, pageable);
        result.instances = flowInstanceRepository.findByFlowId(pageable, id);
        result.currentFlowId = id;

        for (FlowInstance flowInstance : result.instances) {
            Flow flow = flowCache.findById(flowInstance.getFlowId());
            if (flow==null) {
                log.warn("Found flowInstance with wrong flowId. flowId: {}", flowInstance.getFlowId());
                continue;
            }
            result.flows.put(flowInstance.getId(), flow);
        }
        int i=0;
    }

    @GetMapping(value = "/flow-instance-add/{id}")
    public String flowInstanceAdd(@ModelAttribute("result") FlowListResult result, @PathVariable Long id) {
        result.items = flowRepository.findAll();
        result.currentFlowId = id;

        return "launchpad/flow/flow-instance-add";
    }

    @PostMapping("/flow-instance-add-commit")
    public String flowInstanceAddCommit(@ModelAttribute("result") FlowListResult result, Model model, Long flowId, String poolCode, final RedirectAttributes redirectAttributes) {
        if (StringUtils.isBlank(poolCode)) {
            model.addAttribute("errorMessage", "#560.60 inputResourcePoolCode of FlowInstance is empty");
            return "launchpad/flow/flow-instance-add";
        }

        Flow flow = flowCache.findById(flowId);
        if (flow == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.70 flow wasn't found, flowId: " + flowId);
            return "redirect:/launchpad/flow/flows";
        }
        FlowService.TaskProducingResult producingResult = flowService.createFlowInstance(flow, poolCode);
        if (producingResult.flowProducingStatus!= Enums.FlowProducingStatus.OK) {
            result.items = flowRepository.findAll();
            model.addAttribute("errorMessage", "#560.72 Error creating flowInstance: " + producingResult.flowProducingStatus);
            return "launchpad/flow/flow-instance-add";

        }
        return "redirect:/launchpad/flow/flow-instances/" + flowId;
    }

    @GetMapping(value = "/flow-instance-edit/{flowId}/{flowInstanceId}")
    public String flowInstanceEdit(@PathVariable Long flowId, @PathVariable Long flowInstanceId, Model model, final RedirectAttributes redirectAttributes) {
        String redirectUrl = prepareModel(flowId, flowInstanceId, model, redirectAttributes);
        if (redirectUrl != null) {
            return redirectUrl;
        }
        return "launchpad/flow/flow-instance-edit/" + flowId +'/' + flowInstanceId;
    }

    @GetMapping("/flow-instance-delete/{flowId}/{flowInstanceId}")
    public String flowInstanceDelete(@PathVariable Long flowId, @PathVariable Long flowInstanceId, Model model, final RedirectAttributes redirectAttributes) {
        String redirectUrl = prepareModel(flowId, flowInstanceId, model, redirectAttributes);
        if (redirectUrl != null) {
            return redirectUrl;
        }
        return "launchpad/flow/flow-instance-delete";
    }

    private String prepareModel(@PathVariable Long flowId, @PathVariable Long flowInstanceId, Model model, RedirectAttributes redirectAttributes) {
        if (flowId==null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.83 flow wasn't found, flowId: " + flowId);
            return "redirect:/launchpad/flow/flows";
        }
        if (flowInstanceId==null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.85 flow wasn't found, flowId: " + flowId);
            return "redirect:/launchpad/flow/flows";
        }
        final FlowInstance flowInstance = flowInstanceRepository.findById(flowInstanceId).orElse(null);
        if (flowInstance == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.87 flow wasn't found, flowId: " + flowId);
            return "redirect:/launchpad/flow/flows";
        }
        Flow flow = flowCache.findById(flowId);
        if (flow == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.89 flow wasn't found, flowId: " + flowId);
            return "redirect:/launchpad/flow/flows";
        }

        if (!flow.getId().equals(flowInstance.flowId)) {
            flowInstance.valid=false;
            flowInstanceRepository.save(flowInstance);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "#560.73 flowId doesn't match to flowInstance.flowId, flowId: " + flowId+", flowInstance.flowId: " + flowInstance.flowId);
            return "redirect:/launchpad/flow/flows";
        }

        FlowInstanceResult result = new FlowInstanceResult(flowInstance, flow);
        model.addAttribute("result", result);
        return null;
    }

    @PostMapping("/flow-instance-delete-commit")
    public String flowInstanceDeleteCommit(Long flowId, Long flowInstanceId, Model model, final RedirectAttributes redirectAttributes) {
        String redirectUrl = prepareModel(flowId, flowInstanceId, model, redirectAttributes);
        if (redirectUrl != null) {
            return redirectUrl;
        }

        FlowInstance fi = flowInstanceRepository.findById(flowInstanceId).orElse(null);
        if (fi==null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "#560.77 FlowInstance wasn't found, flowInstanceId: " + flowInstanceId );
            return "redirect:/launchpad/flow/flows";
        }
        experimentService.resetExperiment(fi);
        flowInstanceService.deleteById(flowInstanceId);
        taskExperimentFeatureRepository.deleteByFlowInstanceId(flowInstanceId);
        binaryDataService.deleteByFlowInstanceId(flowInstanceId);
        List<FlowInstance> instances = flowInstanceRepository.findByFlowId(fi.flowId);
        if (instances.isEmpty()) {
            Flow flow = flowRepository.findById(fi.flowId).orElse(null);
            if (flow!=null) {
                flow.locked = false;
                flowCache.save(flow);
            }}
        return "redirect:/launchpad/flow/flow-instances/"+ flowId;
    }

    @GetMapping("/flow-instance-target-exec-state/{flowId}/{state}/{id}")
    public String flowInstanceTargetExecState(@PathVariable Long flowId, @PathVariable String state, @PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        Enums.FlowInstanceExecState execState = Enums.FlowInstanceExecState.valueOf(state.toUpperCase());
        if (execState== Enums.FlowInstanceExecState.UNKNOWN) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "#560.79 Unknown exec state, state: " + state);
            return "redirect:/launchpad/flow/flow-instances/" + flowId;
        }
        String redirectUrl = prepareModel(flowId, id, model, redirectAttributes);
        if (redirectUrl != null) {
            return redirectUrl;
        }
        FlowInstanceResult result = (FlowInstanceResult) model.asMap().get("result");
        if (result==null) {
            throw new IllegalStateException("FlowInstanceResult is null");
        }

        result.flowInstance.setExecState(execState.code);
        flowInstanceRepository.save(result.flowInstance);

        result.flow.locked = true;
        flowCache.save(result.flow);

        return "redirect:/launchpad/flow/flow-instances/" + flowId;
    }
}
