import { FlowInstance } from '@app/models/FlowInstance'
import { Features } from '@app/models/Features'


export interface ExperimentInfo {
  allDatasetOptions?: (null)[] | null;
  features?: (Features)[] | null;
  flowInstance: FlowInstance;
  flowInstanceExecState: string;
}