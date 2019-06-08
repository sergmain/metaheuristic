export namespace FlowsResponse {
    export interface Response {
        errorMessages ? : null;
        infoMessages ? : null;
        items: Items;
    }
    export interface Items {
        content ? : (Flow)[] | null;
        pageable: Pageable;
        size: number;
        number: number;
        sort: Sort;
        numberOfElements: number;
        first: boolean;
        last: boolean;
        empty: boolean;
    }
    export interface Flow {
        id: number; 
        version: number;
        code: string;
        createdOn: number;
        params ? : null;
        locked: boolean;
        valid: boolean;
        clean: boolean;
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
}