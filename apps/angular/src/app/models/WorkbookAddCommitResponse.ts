export namespace WorkbookAddCommitResponse {
    export interface Response {
        errorMessages ? : null;
        infoMessages ? : null;
        workbook: Workbook;
        plan: Plan;
    }
    export interface Workbook {
        id: number;
        version: number;
        planId: number;
        createdOn: number;
        completedOn ? : null;
        inputResourceParam: string;
        producingOrder: number;
        valid: boolean;
        execState: number;
    }
    export interface Plan {
        id: number;
        version: number;
        code: string;
        createdOn: number;
        params: string;
        locked: boolean;
        valid: boolean;
        clean: boolean;
    }
}