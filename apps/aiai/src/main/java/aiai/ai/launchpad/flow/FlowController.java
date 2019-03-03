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

package aiai.ai.launchpad.flow;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/launchpad/flow")
@Slf4j
@Profile("launchpad")
public class FlowController {

    private static final String REDIRECT_LAUNCHPAD_FLOW_FLOWS = "redirect:/launchpad/flow/flows";

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
        public Flow flow;
        public long currentFlowId;
    }

    private final Globals globals;
    private final FlowCache flowCache;
    private final FlowService flowService;
    private final FlowRepository flowRepository;
    private final FlowInstanceRepository flowInstanceRepository;

    public FlowController(Globals globals, FlowCache flowCache, FlowService flowService, FlowRepository flowRepository1, FlowInstanceRepository flowInstanceRepository) {
        this.globals = globals;
        this.flowCache = flowCache;
        this.flowService = flowService;
        this.flowRepository = flowRepository1;
        this.flowInstanceRepository = flowInstanceRepository;
    }

    @GetMapping("/flows")
    public String flows(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable, @ModelAttribute("errorMessage") final String errorMessage) {
        pageable = ControllerUtils.fixPageSize(globals.flowRowsLimit, pageable);
        result.items = flowRepository.findAllByOrderByIdDesc(pageable);
        return "launchpad/flow/flows";
    }

    // for AJAX
    @PostMapping("/flows-part")
    public String flowsPart(@ModelAttribute Result result, @PageableDefault(size = 10) Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.flowRowsLimit, pageable);
        result.items = flowRepository.findAllByOrderByIdDesc(pageable);
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
            redirectAttributes.addFlashAttribute("errorMessage", "#560.01 flow wasn't found, flowId: " + id);
            return REDIRECT_LAUNCHPAD_FLOW_FLOWS;
        }
        model.addAttribute("flow", flow);
        return "launchpad/flow/flow-edit";
    }

    @GetMapping(value = "/flow-validate/{id}")
    public String validate(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        final Flow flow = flowCache.findById(id);
        if (flow == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.02 flow wasn't found, flowId: " + id);
            return REDIRECT_LAUNCHPAD_FLOW_FLOWS;
        }
        model.addAttribute("flow", flow);

        FlowData.FlowValidation flowValidation = flowService.validateInternal(flow);
        model.addAttribute("flow", flow);
        model.addAttribute("errorMessage", flowValidation.errorMessage);
        model.addAttribute("infoMessage", flowValidation.infoMessages);
        return "launchpad/flow/flow-edit";
    }

    @PostMapping("/flow-add-commit")
    public String addFormCommit(Model model, Flow flow, final RedirectAttributes redirectAttributes) {
        return processFlowCommit(model, flow, "launchpad/flow/flow-add", REDIRECT_LAUNCHPAD_FLOW_FLOWS, redirectAttributes);
    }

    @PostMapping("/flow-edit-commit")
    public String editFormCommit(Model model, Flow flowModel, final RedirectAttributes redirectAttributes) {
        Flow flow = flowCache.findById(flowModel.getId());
        if (flow == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.10 flow wasn't found, flowId: " + flowModel.getId());
            return REDIRECT_LAUNCHPAD_FLOW_FLOWS;
        }
        flow.setCode(flowModel.getCode());
        flow.setParams(flowModel.getParams());
        return processFlowCommit(model, flow,"launchpad/flow/flow-edit","redirect:/launchpad/flow/flow-edit/"+flow.getId(), redirectAttributes);
    }

    private String processFlowCommit(Model model, Flow flow, String errorTarget, String normalTarget, final RedirectAttributes redirectAttributes) {
        if (StringUtils.isBlank(flow.code)) {
            model.addAttribute("errorMessage", "#560.20 code of flow is empty");
            return errorTarget;
        }
        if (StringUtils.isBlank(flow.code)) {
            model.addAttribute("errorMessage", "#560.30 flow is empty");
            return errorTarget;
        }
        Flow f = flowRepository.findByCode(flow.code);
        if (f!=null && !f.getId().equals(flow.getId())) {
            model.addAttribute("errorMessage", "#560.33 flow with such code already exists, code: " + flow.code);
            return errorTarget;
        }
        flow = flowCache.save(flow);
        FlowData.FlowValidation flowValidation = flowService.validateInternal(flow);
        if (flowValidation.status == Enums.FlowValidateStatus.OK ) {
            redirectAttributes.addFlashAttribute("infoMessages", Collections.singletonList("Validation result: OK"));
        }
        return normalTarget;
    }

    @GetMapping("/flow-delete/{id}")
    public String delete(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        Flow flow = flowCache.findById(id);
        if (flow == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.40 flow wasn't found, id: "+id );
            return REDIRECT_LAUNCHPAD_FLOW_FLOWS;
        }
        model.addAttribute("flow", flow);
        return "launchpad/flow/flow-delete";
    }

    @PostMapping("/flow-delete-commit")
    public String deleteCommit(Long id, final RedirectAttributes redirectAttributes) {
        Flow flow = flowCache.findById(id);
        if (flow == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.50 flow wasn't found, flowId: " + id);
            return REDIRECT_LAUNCHPAD_FLOW_FLOWS;
        }
        flowCache.deleteById(id);
        return REDIRECT_LAUNCHPAD_FLOW_FLOWS;
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
        FlowData.FlowInstancesResultRest r = flowService.getFlowInstancesResult(id, pageable);
        result.instances = r.instances;
        result.currentFlowId = id;
        result.flows = r.flows;
    }

    @GetMapping(value = "/flow-instance-add/{id}")
    public String flowInstanceAdd(@ModelAttribute("result") FlowListResult result, @PathVariable Long id, final RedirectAttributes redirectAttributes) {
        result.flow = flowCache.findById(id);
        if (result.flow == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.55 flow wasn't found, flowId: " + id);
            return REDIRECT_LAUNCHPAD_FLOW_FLOWS;
        }
        result.currentFlowId = id;
        return "launchpad/flow/flow-instance-add";
    }

    @PostMapping("/flow-instance-add-commit")
    public String flowInstanceAddCommit(@ModelAttribute("result") FlowListResult result, Model model, Long flowId, String poolCode, String inputResourceParams, final RedirectAttributes redirectAttributes) {
        result.flow = flowCache.findById(flowId);
        if (result.flow == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.60 flow wasn't found, flowId: " + flowId);
            return REDIRECT_LAUNCHPAD_FLOW_FLOWS;
        }

        if (StringUtils.isBlank(poolCode) && StringUtils.isBlank(inputResourceParams) ) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.63 both inputResourcePoolCode of FlowInstance and inputResourceParams are empty");
            return "redirect:/launchpad/flow/flow-instance-add/" + flowId;
        }

        if (StringUtils.isNotBlank(poolCode) && StringUtils.isNotBlank(inputResourceParams) ) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.65 both inputResourcePoolCode of FlowInstance and inputResourceParams aren't empty");
            return "redirect:/launchpad/flow/flow-instance-add/" + flowId;
        }

        // validate the flow
        FlowData.FlowValidation flowValidation = flowService.validateInternal(result.flow);
        if (flowValidation.status != Enums.FlowValidateStatus.OK ) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.70 validation of flow was failed, status: " + flowValidation.status);
            return "redirect:/launchpad/flow/flow-instance-add/" + flowId;
        }

        FlowService.TaskProducingResult producingResult = flowService.createFlowInstance(result.flow, 
                StringUtils.isNotBlank(inputResourceParams) ?inputResourceParams : FlowService.asInputResourceParams(poolCode));
        if (producingResult.flowProducingStatus!= Enums.FlowProducingStatus.OK) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.72 Error creating flowInstance: " + producingResult.flowProducingStatus);
            return "redirect:/launchpad/flow/flow-instance-add/" + flowId;
        }

        // ugly work-around on StaleObjectStateException
        result.flow = flowCache.findById(flowId);
        if (result.flow == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.73 flow wasn't found, flowId: " + flowId);
            return REDIRECT_LAUNCHPAD_FLOW_FLOWS;
        }

        // validate the flow + the flow instance
        flowValidation = flowService.validateInternal(result.flow);
        if (flowValidation.status != Enums.FlowValidateStatus.OK ) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.75 validation of flow was failed, status: " + flowValidation.status);
            return "redirect:/launchpad/flow/flow-instance-add/" + flowId;
        }

        FlowService.TaskProducingResult countTasks = new FlowService.TaskProducingResult();
        flowService.produceTasks(false, countTasks, result.flow, producingResult.flowInstance);
        if (countTasks.flowProducingStatus != Enums.FlowProducingStatus.OK) {
            redirectAttributes.addFlashAttribute("errorMessage", "#560.77 validation of flow was failed, status: " + countTasks.flowValidateStatus);
            return "redirect:/launchpad/flow/flow-instance-add/" + flowId;
        }

        if (globals.maxTasksPerFlow < countTasks.numberOfTasks) {
            flowService.changeValidStatus(producingResult.flowInstance, false);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "#560.81 number of tasks for this flow instance exceeded the allowed maximum number. Flow instance was created but its status is 'not valid'. " +
                    "Allowed maximum number of tasks: " + globals.maxTasksPerFlow+", tasks in this flow instance:  " + countTasks.numberOfTasks);
            return "redirect:/launchpad/flow/flow-instances/" + flowId;
        }
        flowService.changeValidStatus(producingResult.flowInstance, true);

        return "redirect:/launchpad/flow/flow-instances/" + flowId;
    }

    @SuppressWarnings("Duplicates")
    @GetMapping(value = "/flow-instance-edit/{flowId}/{flowInstanceId}")
    public String flowInstanceEdit(@PathVariable Long flowId, @PathVariable Long flowInstanceId, final RedirectAttributes redirectAttributes) {
        FlowData.FlowInstanceResultRest result = flowService.prepareModel(flowId, flowInstanceId);
        if (result.errorMessage != null) {
            redirectAttributes.addFlashAttribute("errorMessage",result.errorMessage);
            return REDIRECT_LAUNCHPAD_FLOW_FLOWS;
        }
        return "launchpad/flow/flow-instance-edit/" + flowId +'/' + flowInstanceId;
    }

    @SuppressWarnings("Duplicates")
    @GetMapping("/flow-instance-delete/{flowId}/{flowInstanceId}")
    public String flowInstanceDelete(@PathVariable Long flowId, @PathVariable Long flowInstanceId, final RedirectAttributes redirectAttributes) {
        FlowData.FlowInstanceResultRest result = flowService.prepareModel(flowId, flowInstanceId);
        if (result.errorMessage != null) {
            redirectAttributes.addFlashAttribute("errorMessage",result.errorMessage);
            return REDIRECT_LAUNCHPAD_FLOW_FLOWS;
        }
        return "launchpad/flow/flow-instance-delete";
    }

    @SuppressWarnings("Duplicates")
    @PostMapping("/flow-instance-delete-commit")
    public String flowInstanceDeleteCommit(Long flowId, Long flowInstanceId, final RedirectAttributes redirectAttributes) {
        FlowData.FlowInstanceResultRest result = flowService.prepareModel(flowId, flowInstanceId);
        if (result.errorMessage != null) {
            redirectAttributes.addFlashAttribute("errorMessage",result.errorMessage);
            return REDIRECT_LAUNCHPAD_FLOW_FLOWS;
        }
        FlowInstance fi = flowInstanceRepository.findById(flowInstanceId).orElse(null);
        if (fi==null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "#560.84 FlowInstance wasn't found, flowInstanceId: " + flowInstanceId );
            return REDIRECT_LAUNCHPAD_FLOW_FLOWS;
        }
        flowService.deleteFlowInstance(flowInstanceId, fi);
        return "redirect:/launchpad/flow/flow-instances/"+ flowId;
    }

    @GetMapping("/flow-instance-target-exec-state/{flowId}/{state}/{id}")
    public String flowInstanceTargetExecState(@PathVariable Long flowId, @PathVariable String state, @PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        Enums.FlowInstanceExecState execState = Enums.FlowInstanceExecState.valueOf(state.toUpperCase());
        if (execState== Enums.FlowInstanceExecState.UNKNOWN) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "#560.87 Unknown exec state, state: " + state);
            return "redirect:/launchpad/flow/flow-instances/" + flowId;
        }
        FlowData.OperationStatusRest status = flowService.flowInstanceTargetExecState(flowId, id, execState);
        if (status.errorMessage != null) {
            redirectAttributes.addFlashAttribute("errorMessage", status.errorMessage);
            return REDIRECT_LAUNCHPAD_FLOW_FLOWS;
        }

        return "redirect:/launchpad/flow/flow-instances/" + flowId;
    }

}
