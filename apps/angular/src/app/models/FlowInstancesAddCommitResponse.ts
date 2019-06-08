export namespace FlowInstancesAddCommitResponse {
    export interface Response {
        errorMessages ? : null;
        infoMessages ? : null;
        flowInstance: FlowInstance;
        flow: Flow;
    }
    export interface FlowInstance {
        id: number;
        version: number;
        flowId: number;
        createdOn: number;
        completedOn ? : null;
        inputResourceParam: string;
        producingOrder: number;
        valid: boolean;
        execState: number;
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