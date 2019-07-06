import { jsonToUrlParams as toURL } from '@app/helpers/jsonToUrlParams';
import { environment } from 'environments/environment';

const base: string = environment.baseUrl + 'launchpad/plan';

const urls: any = {
    plan: {
        get: (id: string | number): string => `${base}/plan/${id}`,
        add: (data: any): string => `${base}/plan-add-commit/?${toURL(data)}`,
        edit: (): string => `${base}/plan-edit-commit/`,
        validate: (id: string | number): string => `${base}/plan-validate/${id}`,
        delete: (data: any): string => `${base}/plan-delete-commit?${toURL(data)}`,
        archive: (data: any): string => `${base}/plan-archive-commit?${toURL(data)}`
    },
    plans: {
        get: (data: any): string => base + '/plans?' + toURL(data)
    },
    workbooks: {
        // get: (planId, data) => base + '/workbook/' + planId
        get: (planId: string | number, data: any): string => `${base}/workbooks/${planId}?${toURL(data)}`,
    },
    workbook: {
        get: (planId: string | number, workbookId: string | number): string => `${base}/workbook/${planId}/${workbookId}`,
        addCommit: (data: any): string => `${base}/workbook-add-commit/?${toURL(data)}`,
        deleteCommit: (data: any): string => `${base}/workbook-delete-commit/?${toURL(data)}`,
        targetExecState: (planId: string | number, state: string | number, id: string | number): string => {
            return `${base}/workbook-target-exec-state/${planId}/${state}/${id}`;
        }
    }
};

export { urls };