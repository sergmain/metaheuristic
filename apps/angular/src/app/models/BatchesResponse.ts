export namespace BatchesResponse {
    export interface Response {
        errorMessages ? : null;
        infoMessages ? : null;
        batches: Batches;
        errorMessagesAsStr: string;
    }
    export interface Batches {
        content ? : (ContentEntity)[] | null;
        pageable: Pageable;
        totalPages: number;
        last: boolean;
        totalElements: number;
        size: number;
        sort: Sort;
        first: boolean;
        numberOfElements: number;
        number: number;
        empty: boolean;
    }
    export interface ContentEntity {
        batch: Batch;
        planCode: string;
        execStateStr: string;
        execState: number;
        ok: boolean;
    }
    export interface Batch {
        id: number;
        version: number;
        planId: number;
        createdOn: number;
        execState: number;
        params: string;
    }
    export interface Pageable {
        sort: Sort;
        offset: number;
        pageSize: number;
        pageNumber: number;
        paged: boolean;
        unpaged: boolean;
    }
    export interface Sort {
        unsorted: boolean;
        sorted: boolean;
        empty: boolean;
    }
}