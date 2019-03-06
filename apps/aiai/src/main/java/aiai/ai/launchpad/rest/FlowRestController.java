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

package aiai.ai.launchpad.rest;

import aiai.ai.Globals;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.flow.FlowCache;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.FlowRepository;
import aiai.ai.launchpad.rest.data.FlowData;
import aiai.ai.launchpad.rest.data.OperationStatusRest;
import aiai.ai.utils.ControllerUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import static aiai.ai.Enums.*;

@RestController
@RequestMapping("/ng/launchpad/flow")
@Slf4j
@Profile("launchpad")
@CrossOrigin(origins="*", maxAge=3600)
public class FlowRestController {

    private final Globals globals;
    private final FlowCache flowCache;
    private final FlowService flowService;
    private final FlowRepository flowRepository;
    private final FlowInstanceRepository flowInstanceRepository;

    public FlowRestController(Globals globals, FlowCache flowCache, FlowService flowService, FlowRepository flowRepository1, FlowInstanceRepository flowInstanceRepository) {
        this.globals = globals;
        this.flowCache = flowCache;
        this.flowService = flowService;
        this.flowRepository = flowRepository1;
        this.flowInstanceRepository = flowInstanceRepository;
    }

    @GetMapping("/flows")
    public FlowData.FlowsResultRest flows(@PageableDefault(size = 5) Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.flowRowsLimit, pageable);
        FlowData.FlowsResultRest result = new FlowData.FlowsResultRest();
        result.items = flowRepository.findAllByOrderByIdDesc(pageable);
        result.items.forEach( o -> o.params = null );
        return result;
    }

    @GetMapping(value = "/flow/{id}")
    public FlowData.FlowResultRest edit(@PathVariable Long id) {
        final Flow flow = flowCache.findById(id);
        if (flow == null) {
            return new FlowData.FlowResultRest(
                    "#560.01 flow wasn't found, flowId: " + id,
                    FlowValidateStatus.FLOW_NOT_FOUND_ERROR );
        }
        return new FlowData.FlowResultRest(flow);
    }

    @GetMapping(value = "/flow-validate/{id}")
    public FlowData.FlowResultRest validate(@PathVariable Long id) {
        final Flow flow = flowCache.findById(id);
        if (flow == null) {
            return new FlowData.FlowResultRest("#560.02 flow wasn't found, flowId: " + id,
                    FlowValidateStatus.FLOW_NOT_FOUND_ERROR );
        }
        FlowData.FlowResultRest result = new FlowData.FlowResultRest(flow);
        FlowData.FlowValidation flowValidation = flowService.validateInternal(flow);
        result.errorMessage = flowValidation.errorMessage;
        result.infoMessages = flowValidation.infoMessages;
        result.status = flowValidation.status;
        return result;
    }

    @PostMapping("/flow-add-commit")
    public FlowData.FlowResultRest addFormCommit(@RequestBody Flow flow) {
        return processFlowCommit(flow);
    }

    @PostMapping("/flow-edit-commit")
    public FlowData.FlowResultRest editFormCommit(Flow flowModel) {
        Flow flow = flowCache.findById(flowModel.getId());
        if (flow == null) {
            return new FlowData.FlowResultRest(
                    "#560.10 flow wasn't found, flowId: " + flowModel.getId(),
                    FlowValidateStatus.FLOW_NOT_FOUND_ERROR );
        }
        flow.setCode(flowModel.getCode());
        flow.setParams(flowModel.getParams());
        return processFlowCommit(flow);
    }

    private FlowData.FlowResultRest processFlowCommit(Flow flow) {
        if (StringUtils.isBlank(flow.code)) {
            return new FlowData.FlowResultRest("#560.20 code of flow is empty");
        }
        if (StringUtils.isBlank(flow.code)) {
            return new FlowData.FlowResultRest("#560.30 flow is empty");
        }
        Flow f = flowRepository.findByCode(flow.code);
        if (f!=null && !f.getId().equals(flow.getId())) {
            return new FlowData.FlowResultRest("#560.33 flow with such code already exists, code: " + flow.code);
        }
        FlowData.FlowResultRest result = new FlowData.FlowResultRest(flowCache.save(flow));
        FlowData.FlowValidation flowValidation = flowService.validateInternal(flow);
        result.infoMessages = flowValidation.infoMessages;
        result.errorMessage = flowValidation.errorMessage;
        return result;
    }

    @PostMapping("/flow-delete-commit")
    public OperationStatusRest deleteCommit(Long id) {
        Flow flow = flowCache.findById(id);
        if (flow == null) {
            return new OperationStatusRest(OperationStatus.ERROR,
                    "#560.50 flow wasn't found, flowId: " + id);
        }
        flowCache.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    // ============= Flow instances =============

    @GetMapping("/flow-instances/{id}")
    public FlowData.FlowInstancesResultRest flowInstances(@PathVariable Long id, @PageableDefault(size = 5) Pageable pageable) {
        return flowService.getFlowInstancesResult(id, pageable);
    }

    // just use "/flow/{id}"
/*
    @GetMapping(value = "/flow-instance-add/{id}")
    public FlowResultRest flowInstanceAdd(@PathVariable Long id) {
        FlowResultRest result = new FlowResultRest(flowCache.findById(id));
        if (result.flow == null) {
            result.errorMessage = "#560.55 flow wasn't found, flowId: " + id;
            return result;
        }
        return result;
    }
*/

    @PostMapping("/flow-instance-add-commit")
    public FlowData.FlowInstanceResultRest flowInstanceAddCommit(Long flowId, String poolCode, String inputResourceParams) {
        if (StringUtils.isBlank(poolCode) && StringUtils.isBlank(inputResourceParams) ) {
            return new FlowData.FlowInstanceResultRest("#560.63 both inputResourcePoolCode of FlowInstance and inputResourceParams are empty");
        }

        if (StringUtils.isNotBlank(poolCode) && StringUtils.isNotBlank(inputResourceParams) ) {
            return new FlowData.FlowInstanceResultRest("#560.65 both inputResourcePoolCode of FlowInstance and inputResourceParams aren't empty");
        }

        FlowData.FlowInstanceResultRest result = new FlowData.FlowInstanceResultRest(flowCache.findById(flowId));
        if (result.flow == null) {
            result.errorMessage = "#560.60 flow wasn't found, flowId: " + flowId;
            return result;
        }

        // validate the flow
        FlowData.FlowValidation flowValidation = flowService.validateInternal(result.flow);
        if (flowValidation.status != FlowValidateStatus.OK ) {
            return new FlowData.FlowInstanceResultRest("#560.70 validation of flow was failed, status: " + flowValidation.status);
        }

        FlowService.TaskProducingResult producingResult = flowService.createFlowInstance(result.flow, 
                StringUtils.isNotBlank(inputResourceParams) ?inputResourceParams : FlowService.asInputResourceParams(poolCode));
        if (producingResult.flowProducingStatus!= FlowProducingStatus.OK) {
            return new FlowData.FlowInstanceResultRest("#560.72 Error creating flowInstance: " + producingResult.flowProducingStatus);
        }
        result.flowInstance = producingResult.flowInstance;

        // ugly work-around on StaleObjectStateException
        result.flow = flowCache.findById(flowId);
        if (result.flow == null) {
            return new FlowData.FlowInstanceResultRest("#560.73 flow wasn't found, flowId: " + flowId);
        }

        // validate the flow + the flow instance
        flowValidation = flowService.validateInternal(result.flow);
        if (flowValidation.status != FlowValidateStatus.OK ) {
            return new FlowData.FlowInstanceResultRest("#560.75 validation of flow was failed, status: " + flowValidation.status);
        }

        FlowService.TaskProducingResult countTasks = new FlowService.TaskProducingResult();
        flowService.produceTasks(false, countTasks, result.flow, producingResult.flowInstance);
        if (countTasks.flowProducingStatus != FlowProducingStatus.OK) {
            return new FlowData.FlowInstanceResultRest("#560.77 validation of flow was failed, status: " + countTasks.flowValidateStatus);
        }

        if (globals.maxTasksPerFlow < countTasks.numberOfTasks) {
            flowService.changeValidStatus(producingResult.flowInstance, false);
            return new FlowData.FlowInstanceResultRest(
                    "#560.81 number of tasks for this flow instance exceeded the allowed maximum number. Flow instance was created but its status is 'not valid'. " +
                    "Allowed maximum number of tasks: " + globals.maxTasksPerFlow+", tasks in this flow instance:  " + countTasks.numberOfTasks);
        }
        flowService.changeValidStatus(producingResult.flowInstance, true);

        return result;
    }

    @GetMapping(value = "/flow-instance/{flowId}/{flowInstanceId}")
    public FlowData.FlowInstanceResultRest flowInstanceEdit(@PathVariable Long flowId, @PathVariable Long flowInstanceId) {
        //noinspection UnnecessaryLocalVariable
        FlowData.FlowInstanceResultRest result = flowService.prepareModel(flowId, flowInstanceId);
        return result;
    }

    @PostMapping("/flow-instance-delete-commit")
    public OperationStatusRest flowInstanceDeleteCommit(Long flowId, Long flowInstanceId) {
        FlowData.FlowInstanceResultRest result = flowService.prepareModel(flowId, flowInstanceId);
        if (result.errorMessage != null) {
            return new OperationStatusRest(OperationStatus.ERROR, result.errorMessage);
        }

        FlowInstance fi = flowInstanceRepository.findById(flowInstanceId).orElse(null);
        if (fi==null) {
            return new OperationStatusRest(OperationStatus.ERROR, "#560.84 FlowInstance wasn't found, flowInstanceId: " + flowInstanceId );
        }
        flowService.deleteFlowInstance(flowInstanceId, fi);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @GetMapping("/flow-instance-target-exec-state/{flowId}/{state}/{id}")
    public OperationStatusRest flowInstanceTargetExecState(@PathVariable Long flowId, @PathVariable String state, @PathVariable Long id) {
        FlowInstanceExecState execState = FlowInstanceExecState.valueOf(state.toUpperCase());
        if (execState== FlowInstanceExecState.UNKNOWN) {
            return new OperationStatusRest(OperationStatus.ERROR, "#560.87 Unknown exec state, state: " + state);
        }
        //noinspection UnnecessaryLocalVariable
        OperationStatusRest status = flowService.flowInstanceTargetExecState(flowId, id, execState);
        return status;
    }

}
