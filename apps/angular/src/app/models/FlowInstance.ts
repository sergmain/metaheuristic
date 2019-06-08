export interface FlowInstance {
  id: number;
  version: number;
  flowId: number;
  createdOn: number;
  completedOn: number;
  inputResourceParam: string;
  producingOrder: number;
  valid: boolean;
  execState: number;
}