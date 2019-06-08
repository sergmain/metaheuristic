export interface Workbook {
  id: number;
  version: number;
  planId: number;
  createdOn: number;
  completedOn: number;
  inputResourceParam: string;
  producingOrder: number;
  valid: boolean;
  execState: number;
}