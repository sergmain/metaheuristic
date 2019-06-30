import { environment } from 'environments/environment';
import jsonToUrlParams from '@app/helpers/jsonToUrlParams';

const base = environment.baseUrl + 'launchpad/plan';

let urls = {
    plan: {
        get: id => `${base}/plan/${id}`,
        add: data => base + `/plan-add-commit/?${jsonToUrlParams(data)}`,
        edit: () => base + '/plan-edit-commit/',
        validate: id => `${base}/plan-validate/${id}`,
        delete: data => base + '/plan-delete-commit?' + jsonToUrlParams(data)
    },
    plans: {
        get: data => base + '/plans?' + jsonToUrlParams(data)
    },
    workbooks: {
        // get: (planId, data) => base + '/workbook/' + planId
        get: (planId, data) => `${base}/workbooks/${planId}?${jsonToUrlParams(data)}`,
    },
    workbook: {
        get: (planId, workbookId) => `${base}/workbook/${planId}/${workbookId}`,
        addCommit: data => `${base}/workbook-add-commit/?${jsonToUrlParams(data)}`,
        deleteCommit: data => `${base}/workbook-delete-commit/?${jsonToUrlParams(data)}`,
        targetExecState: (planId, state, id) => `${base}/workbook-target-exec-state/${planId}/${state}/${id}`
    }
};

export {
    urls
};


// @GetMapping("/workbook/{id}")
// public PlanData.WorkbooksResult workbooks(@PathVariable Long id, @PageableDefault(size = 5) Pageable pageable) {
//     return planTopLevelService.getWorkbooks(id, pageable);
// }

// @PostMapping("/workbook-add-commit")
// public PlanData.WorkbookResult workbookAddCommit(Long planId, String poolCode, String inputResourceParams) {
//     return planTopLevelService.addWorkbook(planId, poolCode, inputResourceParams);
// }

// @GetMapping(value = "/workbook/{planId}/{workbookId}")
// public PlanData.WorkbookResult workbookEdit(@PathVariable Long planId, @PathVariable Long workbookId) {
//     return planTopLevelService.getWorkbookExtended(planId, workbookId);
// }

// @PostMapping("/workbook-delete-commit")
// public OperationStatusRest workbookDeleteCommit(Long planId, Long workbookId) {
//     return planTopLevelService.deleteWorkbookById(planId, workbookId);
// }

// @GetMapping("/workbook-target-exec-state/{planId}/{state}/{id}")
// public OperationStatusRest workbookTargetExecState(@PathVariable Long planId, @PathVariable String state, @PathVariable Long id) {
//     return planTopLevelService.changeWorkbookExecState(planId, state, id);
// }