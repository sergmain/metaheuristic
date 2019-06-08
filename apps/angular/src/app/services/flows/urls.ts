import {
    environment
} from 'environments/environment';
import jsonToUrlParams from '@app/helpers/jsonToUrlParams';

const base = environment.baseUrl + '/ng/launchpad/flow';

let urls = {
    flow: {
        get: id => `${base}/flow/${id}`,
        add: () => base + '/flow-add-commit/',
        edit: () => base + '/flow-edit-commit/',
        validate: id => `${base}/flow-validate/${id}`,
        delete: data => base + '/flow-delete-commit?' + jsonToUrlParams(data)
    },
    flows: {
        get: data => base + '/flows?' + jsonToUrlParams(data)
    },
    instances: {
        // get: (flowId, data) => base + '/flow-instances/' + flowId
        get: (flowId, data) => `${base}/flow-instances/${flowId}?${jsonToUrlParams(data)}`,
    },
    instance: {
        get: (flowId, flowInstanceId) => `${base}/flow-instance/${flowId}/${flowInstanceId}`,
        addCommit: data => `${base}/flow-instance-add-commit/?${jsonToUrlParams(data)}`,
        deleteCommit: data => `${base}/flow-instance-delete-commit/?${jsonToUrlParams(data)}`,
        targetExecState: (flowId, state, id) => `${base}/flow-instance-target-exec-state/${flowId}/${state}/${id}`
    }
};

export {
    urls
};


// @GetMapping("/flow-instances/{id}")
// public FlowData.FlowInstancesResult flowInstances(@PathVariable Long id, @PageableDefault(size = 5) Pageable pageable) {
//     return flowTopLevelService.getFlowInstances(id, pageable);
// }

// @PostMapping("/flow-instance-add-commit")
// public FlowData.FlowInstanceResult flowInstanceAddCommit(Long flowId, String poolCode, String inputResourceParams) {
//     return flowTopLevelService.addFlowInstance(flowId, poolCode, inputResourceParams);
// }

// @GetMapping(value = "/flow-instance/{flowId}/{flowInstanceId}")
// public FlowData.FlowInstanceResult flowInstanceEdit(@PathVariable Long flowId, @PathVariable Long flowInstanceId) {
//     return flowTopLevelService.getFlowInstanceExtended(flowId, flowInstanceId);
// }

// @PostMapping("/flow-instance-delete-commit")
// public OperationStatusRest flowInstanceDeleteCommit(Long flowId, Long flowInstanceId) {
//     return flowTopLevelService.deleteFlowInstanceById(flowId, flowInstanceId);
// }

// @GetMapping("/flow-instance-target-exec-state/{flowId}/{state}/{id}")
// public OperationStatusRest flowInstanceTargetExecState(@PathVariable Long flowId, @PathVariable String state, @PathVariable Long id) {
//     return flowTopLevelService.changeFlowInstanceExecState(flowId, state, id);
// }