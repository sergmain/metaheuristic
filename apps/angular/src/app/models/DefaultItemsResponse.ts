// tslint:disable-next-line: no-namespace
export namespace DefaultItemsResponse {
    export interface Response {
        errorMessages ?: (null)[] | null;
        infoMessages ?: (null)[] | null;
        items: Items;
    }
    export interface Items {
        content ?: (null)[] | null;
        pageable: Pageable;
        last: boolean;
        totalElements: number;
        totalPages: number;
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
        pageSize: number;
        pageNumber: number;
        paged: boolean;
        unpaged: boolean;
    }
    export interface Sort {
        sorted: boolean;
        unsorted: boolean;
        empty: boolean;
    }
}