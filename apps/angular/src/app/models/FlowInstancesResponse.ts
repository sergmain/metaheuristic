export namespace FlowInstancesResponse {
    export interface Response {
        errorMessages ? : null;
        infoMessages ? : null;
        instances: Instances;
        currentFlowId: number;
        flows: (Flow)[];
    }
    export interface Instances {
        content ? : (Instance)[] | null;
        pageable: Pageable;
        size: number;
        number: number;
        sort: Sort;
        numberOfElements: number;
        first: boolean;
        last: boolean;
        empty: boolean;
    }
    export interface Instance {
        id: number;
        version: number;
        flowId: number;
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
    
    export interface Flow {
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