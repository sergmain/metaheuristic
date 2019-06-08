export namespace WorkbooksResponse {
    export interface Response {
        errorMessages ? : null;
        infoMessages ? : null;
        instances: Workbooks;
        currentPlanId: number;
        plans: (Plan)[];
    }
    export interface Workbooks {
        content ? : (Workbook)[] | null;
        pageable: Pageable;
        size: number;
        number: number;
        sort: Sort;
        numberOfElements: number;
        first: boolean;
        last: boolean;
        empty: boolean;
    }
    export interface Workbook {
        id: number;
        version: number;
        planId: number;
        createdOn: number;
        completedOn: number;
        inputResourceParam: string;
        producingOrder: number;
        valid: boolean;
        execState: number; 
    }
    export interface Pageable {
        sort: Sort;
        offset: number;
        pageSize: number;
        pageNumber: number;
        unpaged: boolean;
        paged: boolean;
    }
    export interface Sort {
        sorted: boolean;
        unsorted: boolean;
        empty: boolean;
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