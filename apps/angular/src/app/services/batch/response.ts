import { DefaultResponse } from '@app/models/DefaultResponse';
import { Pageable } from '@app/models/Pageable';
import { Sort } from '@app/models/Sort';
import { Plan } from '@app/models/Plan';

export namespace batches {
    export namespace get {
        export interface Response extends DefaultResponse {
            batches: Batches;
        }
    }
}

export namespace batch {
    export namespace get {
        export interface Response extends DefaultResponse {
            batches: Batches;
        }
    }

    // export namespace delete {
    //     export interface Response extends DefaultResponse {
    //         batches: Batches;
    //     }
    // }
    export namespace status {
        export interface Response extends DefaultResponse {
            batchId: number | string;
            console: string;
            ok: boolean;
        }
    }
    export namespace add {
        export interface Response extends DefaultResponse {
            items: Plan[];
        }
    }
}


export interface Batches {
    content ? : Batch[] | null;
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

export interface Batch {
    batch: {
        id: number;
        version: number;
        planId: number;
        createdOn: number;
        execState: number;
        params: string;
    };
    planCode: string;
    execStateStr: string;
    execState: number;
    ok: boolean;
}


export interface Plan {
    code: string;
    createdOn: number;
    id: string | number;
    locked: boolean;
    params: string;
    valid: boolean;
    version: number;
}