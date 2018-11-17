package aiai.ai.launchpad.flow;

import aiai.ai.Globals;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.FlowRepository;
import aiai.ai.utils.ControllerUtils;
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

@Controller
@RequestMapping("/launchpad/flow")
@Slf4j
@Profile("launchpad")
public class FlowController {

    @Data
    public static class Result {
        public Slice<Flow> items;
        public Slice<FlowInstance> instances;
    }

    private final Globals globals;
    private final FlowRepository flowRepository;
    private final FlowInstanceRepository flowInstanceRepository;

    public FlowController(Globals globals, FlowRepository flowRepository, FlowInstanceRepository flowInstanceRepository) {
        this.globals = globals;
        this.flowRepository = flowRepository;
        this.flowInstanceRepository = flowInstanceRepository;
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
        return "launchpad/flow/flow-add-form";
    }

    @GetMapping(value = "/flow-edit/{id}")
    public String edit(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        final Flow flow = flowRepository.findById(id).orElse(null);
        if (flow == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#575.01 flow wasn't found, flowId: " + id);
            return "redirect:/launchpad/flow/flows";
        }
        model.addAttribute("flow", flow);
        return "launchpad/flow/flow-edit-form";
    }

    @PostMapping("/flow-add-form-commit")
    public String addFormCommit(Model model, Flow flow) {
        return processFlowCommit(model, flow, "launchpad/flow-add-form", "redirect:/launchpad/flow/flows");
    }

    @PostMapping("/flow-edit-form-commit")
    public String editFormCommit(Model model, Flow flowModel, final RedirectAttributes redirectAttributes) {
        Flow flow = flowRepository.findById(flowModel.getId()).orElse(null);
        if (flow == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#581.01 flow wasn't found, flowId: " + flowModel.getId());
            return "redirect:/launchpad/flow/flows";
        }
        flow.setCode(flowModel.getCode());
        flow.setParams(flowModel.getParams());
        return processFlowCommit(model, flow,"launchpad/flow/flow-edit-form","redirect:/launchpad/flow/flow-edit/"+flow.getId());
    }

    private String processFlowCommit(Model model, Flow flow, String errorTarget, String normalTarget) {
        if (StringUtils.isBlank(flow.code)) {
            model.addAttribute("errorMessage", "#595.10 code of flow is empty");
            return errorTarget;
        }
        if (StringUtils.isBlank(flow.code)) {
            model.addAttribute("errorMessage", "#595.30 flow is empty");
            return errorTarget;
        }
        flowRepository.save(flow);
        return normalTarget;
    }

    @GetMapping("/flow-delete/{id}")
    public String delete(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        Flow flow = flowRepository.findById(id).orElse(null);
        if (flow == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#594.01 experiment wasn't found, id: "+id );
            return "redirect:/launchpad/flow/flows";
        }
        model.addAttribute("flow", flow);
        return "launchpad/flow/flow-delete";
    }

    @PostMapping("/flow-delete-commit")
    public String deleteCommit(Long id, final RedirectAttributes redirectAttributes) {
        Flow flow = flowRepository.findById(id).orElse(null);
        if (flow == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#583.01 flow wasn't found, flowId: " + id);
            return "redirect:/launchpad/flow/flows";
        }
        flowRepository.deleteById(id);
        return "redirect:/launchpad/flow/flows";
    }

    // ============= Flow instances =============

    @GetMapping("/flow-instances/{id}")
    public String flowInstances(@ModelAttribute Result result, @PathVariable Long id, @PageableDefault(size = 5) Pageable pageable, @ModelAttribute("errorMessage") final String errorMessage) {
        pageable = ControllerUtils.fixPageSize(globals.flowInstanceRowsLimit, pageable);
        result.instances = flowInstanceRepository.findByFlowId(pageable, id);
        return "launchpad/flow/flow-instances";
    }

    // for AJAX
    @PostMapping("/flow-instances-part/{id}")
    public String flowInstancesPart(@ModelAttribute Result result, @PathVariable Long id, @PageableDefault(size = 10) Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.flowInstanceRowsLimit, pageable);
        result.instances = flowInstanceRepository.findByFlowId(pageable, id);
        return "launchpad/flow/flow-instances :: table";
    }

    @GetMapping(value = "/flow-instance-add")
    public String flowInstanceAdd(@ModelAttribute("flowInstance") FlowInstance flowInstance) {
        return "launchpad/flow/flow-instance-add-form";
    }

    @GetMapping(value = "/flow-instance-edit/{flowId}/{flowInstanceId}")
    public String flowInstanceEdit(@PathVariable Long flowId, @PathVariable Long flowInstanceId, Model model, final RedirectAttributes redirectAttributes) {
        final FlowInstance flowInstance = flowInstanceRepository.findById(flowInstanceId).orElse(null);
        if (flowInstance == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#585.01 flow wasn't found, flowId: " + flowId);
            return "redirect:/launchpad/flow/flow-instances";
        }
        model.addAttribute("flowInstance", flowInstance);
        return "launchpad/flow/flow-instance-edit-form/" + flowId +'/' + flowInstanceId;
    }

    @PostMapping("/flow-instance-add-form-commit")
    public String flowInstanceAddCommit(Model model, Long flowId, String poolCode) {
        if (StringUtils.isBlank(poolCode)) {
            model.addAttribute("errorMessage", "#585.10 inputResourcePoolCode of FlowInstance is empty");
            return "launchpad/flow-instance-add-form";
        }

        FlowInstance flowInstance = new FlowInstance();
        flowInstance.setCompletedOrder(0);
        flowInstance.setInputResourcePoolCode(poolCode);
        flowInstance.flowId = flowId;
        flowInstanceRepository.save(flowInstance);

        return "redirect:/launchpad/flow/flow-instances";
    }

}
