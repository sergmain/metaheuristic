export namespace FlowResponse {
    export interface Response {
        errorMessages: (string)[];
        infoMessages ?: null;
        flow: Flow;
        status: string;
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


