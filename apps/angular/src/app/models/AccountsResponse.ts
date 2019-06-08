// tslint:disable-next-line: no-namespace
export namespace AccountsResponse {
    export interface Response {
        errorMessages: null;
        infoMessages: null;
        accounts: Accounts;
    }
    export interface Accounts {
        content: any[];
        pageable: Pageable;
        totalPages: number;
        last: boolean;
        totalElements: number;
        number: number;
        size: number;
        sort: Sort;
        numberOfElements: number;
        first: boolean;
        empty: boolean;
    }
    export interface Pageable {
        sort: Sort;
        offset: number;
        pageNumber: number;
        pageSize: number;
        unpaged: boolean;
        paged: boolean;
    }
    export interface Sort {
        unsorted: boolean;
        sorted: boolean;
        empty: boolean;
    }



}