import { DefaultResponse } from '@app/models/DefaultResponse';
import { Batches } from './Batches'
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
    export namespace upload {
        export interface Response extends DefaultResponse {

        }
    }
}