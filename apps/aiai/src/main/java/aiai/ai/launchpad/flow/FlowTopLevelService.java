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
import aiai.ai.launchpad.data.FlowData;
import aiai.ai.launchpad.data.OperationStatusRest;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.FlowRepository;
import aiai.ai.utils.CollectionUtils;
import aiai.ai.utils.ControllerUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@Slf4j
@Profile("launchpad")
@Service
public class FlowTopLevelService {

    private final Globals globals;
    private final FlowCache flowCache;
    private final FlowService flowService;
    private final FlowRepository flowRepository;
    private final FlowInstanceRepository flowInstanceRepository;

    public FlowTopLevelService(Globals globals, FlowCache flowCache, FlowService flowService, FlowRepository flowRepository1, FlowInstanceRepository flowInstanceRepository) {
        this.globals = globals;
        this.flowCache = flowCache;
        this.flowService = flowService;
        this.flowRepository = flowRepository1;
        this.flowInstanceRepository = flowInstanceRepository;
    }

    public FlowData.FlowsResultRest getFlows(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.flowRowsLimit, pageable);
        FlowData.FlowsResultRest result = new FlowData.FlowsResultRest();
        result.items = flowRepository.findAllByOrderByIdDesc(pageable);
        result.items.forEach( o -> o.params = null );
        return result;
    }

    public FlowData.FlowResultRest getFlow(Long id) {
        final Flow flow = flowCache.findById(id);
        if (flow == null) {
            return new FlowData.FlowResultRest(
                    "#560.01 flow wasn't found, flowId: " + id,
                    Enums.FlowValidateStatus.FLOW_NOT_FOUND_ERROR );
        }
        return new FlowData.FlowResultRest(flow);
    }

    public FlowData.FlowResultRest validateFlow(Long id) {
        final Flow flow = flowCache.findById(id);
        if (flow == null) {
            return new FlowData.FlowResultRest("#560.02 flow wasn't found, flowId: " + id,
                    Enums.FlowValidateStatus.FLOW_NOT_FOUND_ERROR );
        }
        FlowData.FlowResultRest result = new FlowData.FlowResultRest(flow);
        FlowData.FlowValidation flowValidation = flowService.validateInternal(flow);
        result.errorMessages = flowValidation.errorMessages;
        result.infoMessages = flowValidation.infoMessages;
        result.status = flowValidation.status;
        return result;
    }

    public FlowData.FlowResultRest addFlow(Flow flow) {
        return processFlowCommit(flow);
    }

    public FlowData.FlowResultRest updateFlow(Flow flowModel) {
        Flow flow = flowCache.findById(flowModel.getId());
        if (flow == null) {
            return new FlowData.FlowResultRest(
                    "#560.10 flow wasn't found, flowId: " + flowModel.getId(),
                    Enums.FlowValidateStatus.FLOW_NOT_FOUND_ERROR );
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
        FlowData.FlowValidation flowValidation = flowService.validateInternal(result.flow);
        result.infoMessages = flowValidation.infoMessages;
        result.errorMessages = flowValidation.errorMessages;
        return result;
    }

    public OperationStatusRest deleteFlowById(Long id) {
        Flow flow = flowCache.findById(id);
        if (flow == null) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,
                    "#560.50 flow wasn't found, flowId: " + id);
        }
        flowCache.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    // ============= Flow instances =============

    public FlowData.FlowInstancesResultRest getFlowInstances(Long id, Pageable pageable) {
        return flowService.getFlowInstancesResult(id, pageable);
    }

    public FlowData.FlowInstanceResultRest addFlowInstance(Long flowId, String poolCode, String inputResourceParams) {
        if (StringUtils.isBlank(poolCode) && StringUtils.isBlank(inputResourceParams) ) {
            return new FlowData.FlowInstanceResultRest("#560.63 both inputResourcePoolCode of FlowInstance and inputResourceParams are empty");
        }

        if (StringUtils.isNotBlank(poolCode) && StringUtils.isNotBlank(inputResourceParams) ) {
            return new FlowData.FlowInstanceResultRest("#560.65 both inputResourcePoolCode of FlowInstance and inputResourceParams aren't empty");
        }

        FlowData.FlowInstanceResultRest result = new FlowData.FlowInstanceResultRest(flowCache.findById(flowId));
        if (result.flow == null) {
            result.addErrorMessage("#560.60 flow wasn't found, flowId: " + flowId);
            return result;
        }

        // validate the flow
        FlowData.FlowValidation flowValidation = flowService.validateInternal(result.flow);
        if (flowValidation.status != Enums.FlowValidateStatus.OK ) {
            result.errorMessages = flowValidation.errorMessages;
            return result;
        }

        FlowService.TaskProducingResult producingResult = flowService.createFlowInstance(result.flow,
                StringUtils.isNotBlank(inputResourceParams) ?inputResourceParams : FlowService.asInputResourceParams(poolCode));
        if (producingResult.flowProducingStatus!=Enums.FlowProducingStatus.OK) {
/*
            if (producingResult.flowProducingStatus!=Enums.FlowProducingStatus.OK &&
                    producingResult.flowProducingStatus!=Enums.FlowProducingStatus.NOT_VALIDATED_YET_ERROR)  {
*/
            result.addErrorMessage("#560.72 Error creating flowInstance: " + producingResult.flowProducingStatus);
            return result;
        }
        result.flowInstance = producingResult.flowInstance;

        // ugly work-around on StaleObjectStateException
        result.flow = flowCache.findById(flowId);
        if (result.flow == null) {
            return new FlowData.FlowInstanceResultRest("#560.73 flow wasn't found, flowId: " + flowId);
        }

        // validate the flow + the flow instance
        flowValidation = flowService.validateInternal(result.flow);
        if (flowValidation.status != Enums.FlowValidateStatus.OK ) {
            result.errorMessages = flowValidation.errorMessages;
            return result;
        }
        result.flow = flowCache.findById(flowId);

        FlowService.TaskProducingResult countTasks = new FlowService.TaskProducingResult();
        countTasks.flowValidateStatus = Enums.FlowValidateStatus.OK;

        flowService.produceTasks(false, countTasks, result.flow, producingResult.flowInstance);
        if (countTasks.flowProducingStatus != Enums.FlowProducingStatus.OK) {
            flowService.changeValidStatus(producingResult.flowInstance, false);
            result.addErrorMessage("#560.77 flow producing was failed, status: " + countTasks.flowProducingStatus);
            return result;
        }

        if (globals.maxTasksPerFlow < countTasks.numberOfTasks) {
            flowService.changeValidStatus(producingResult.flowInstance, false);
            result.addErrorMessage("#560.81 number of tasks for this flow instance exceeded the allowed maximum number. Flow instance was created but its status is 'not valid'. " +
                    "Allowed maximum number of tasks: " + globals.maxTasksPerFlow+", tasks in this flow instance:  " + countTasks.numberOfTasks);
            return result;
        }
        flowService.changeValidStatus(producingResult.flowInstance, true);

        return result;
    }

    public FlowData.FlowInstanceResultRest getFlowInstanceExtended(Long flowId, Long flowInstanceId) {
        //noinspection UnnecessaryLocalVariable
        FlowData.FlowInstanceResultRest result = flowService.prepareModel(flowId, flowInstanceId);
        return result;
    }

    public OperationStatusRest deleteFlowInstanceById(Long flowId, Long flowInstanceId) {
        FlowData.FlowInstanceResultRest result = flowService.prepareModel(flowId, flowInstanceId);
        if (CollectionUtils.isNotEmpty(result.errorMessages)) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR, result.errorMessages);
        }

        FlowInstance fi = flowInstanceRepository.findById(flowInstanceId).orElse(null);
        if (fi==null) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR, "#560.84 FlowInstance wasn't found, flowInstanceId: " + flowInstanceId );
        }
        flowService.deleteFlowInstance(flowInstanceId, fi);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest changeFlowInstanceExecState(Long flowId, String state, Long flowInstanceId) {
        Enums.FlowInstanceExecState execState = Enums.FlowInstanceExecState.valueOf(state.toUpperCase());
        if (execState==Enums.FlowInstanceExecState.UNKNOWN) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR, "#560.87 Unknown exec state, state: " + state);
        }
        //noinspection UnnecessaryLocalVariable
        OperationStatusRest status = flowService.flowInstanceTargetExecState(flowId, flowInstanceId, execState);
        return status;
    }

}
