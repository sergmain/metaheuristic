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

import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.data.FlowData;
import aiai.ai.launchpad.data.OperationStatusRest;
import aiai.ai.launchpad.flow.FlowTopLevelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ng/launchpad/flow")
@Slf4j
@Profile("launchpad")
@CrossOrigin
//@CrossOrigin(origins="*", maxAge=3600)
public class FlowRestController {

    private final FlowTopLevelService flowTopLevelService;

    public FlowRestController(FlowTopLevelService flowTopLevelService) {
        this.flowTopLevelService = flowTopLevelService;
    }

    @GetMapping("/flows")
    public FlowData.FlowsResultRest flows(@PageableDefault(size = 5) Pageable pageable) {
        return flowTopLevelService.getFlows(pageable);
    }

    @GetMapping(value = "/flow/{id}")
    public FlowData.FlowResultRest edit(@PathVariable Long id) {
        return flowTopLevelService.getFlow(id);
    }

    @GetMapping(value = "/flow-validate/{id}")
    public FlowData.FlowResultRest validate(@PathVariable Long id) {
        return flowTopLevelService.validateFlow(id);
    }

    @PostMapping("/flow-add-commit")
    public FlowData.FlowResultRest addFormCommit(@RequestBody Flow flow) {
        return flowTopLevelService.addFlow(flow);
    }

    @PostMapping("/flow-edit-commit")
    public FlowData.FlowResultRest editFormCommit(Flow flow) {
        return flowTopLevelService.updateFlow(flow);
    }

    @PostMapping("/flow-delete-commit")
    public OperationStatusRest deleteCommit(Long id) {
        return flowTopLevelService.deleteFlowById(id);
    }

    // ============= Flow instances =============

    @GetMapping("/flow-instances/{id}")
    public FlowData.FlowInstancesResultRest flowInstances(@PathVariable Long id, @PageableDefault(size = 5) Pageable pageable) {
        return flowTopLevelService.getFlowInstances(id, pageable);
    }

    @PostMapping("/flow-instance-add-commit")
    public FlowData.FlowInstanceResultRest flowInstanceAddCommit(Long flowId, String poolCode, String inputResourceParams) {
        return flowTopLevelService.addFlowInstance(flowId, poolCode, inputResourceParams);
    }

    @GetMapping(value = "/flow-instance/{flowId}/{flowInstanceId}")
    public FlowData.FlowInstanceResultRest flowInstanceEdit(@PathVariable Long flowId, @PathVariable Long flowInstanceId) {
        return flowTopLevelService.getFlowInstanceExtended(flowId, flowInstanceId);
    }

    @PostMapping("/flow-instance-delete-commit")
    public OperationStatusRest flowInstanceDeleteCommit(Long flowId, Long flowInstanceId) {
        return flowTopLevelService.deleteFlowInstanceById(flowId, flowInstanceId);
    }

    @GetMapping("/flow-instance-target-exec-state/{flowId}/{state}/{id}")
    public OperationStatusRest flowInstanceTargetExecState(@PathVariable Long flowId, @PathVariable String state, @PathVariable Long id) {
        return flowTopLevelService.changeFlowInstanceExecState(flowId, state, id);
    }

}
