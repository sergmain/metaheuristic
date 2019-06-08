// tslint:disable-next-line: no-namespace
export namespace ResourcesResponse {
    export interface Response {
        errorMessages ?: null;
        infoMessages ?: null;
        items: Items;
    }
    export interface Items {
        content ?: (Resource)[] | null;
        pageable: Pageable;
        sort: Sort;
        numberOfElements: number;
        first: boolean;
        last: boolean;
        size: number;
        number: number;
        empty: boolean;
    }
    export interface Resource {
        id: number;
        version: number;
        code: string;
        poolCode: string;
        dataType: number;
        uploadTs: string;
        checksum ?: null;
        valid: boolean;
        manual: boolean;
        filename: string;
        storageUrl: string;
        dataTypeAsStr: string;
    }
    export interface Pageable {
        sort: Sort;
        pageSize: number;
        pageNumber: number;
        offset: number;
        paged: boolean;
        unpaged: boolean;
    }
    export interface Sort {
        sorted: boolean;
        unsorted: boolean;
        empty: boolean;
    }
}