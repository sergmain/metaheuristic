export interface FlowInstanceResponse {
    completedOn: number;
    createdOn: number;
    execState: number;
    flowId: number;
    id: number;
    inputResourceParam: string;
    producingOrder: number;
    valid: boolean;
    version: number;
}