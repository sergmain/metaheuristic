import { Workbook } from '@app/models/Workbook'
import { Features } from '@app/models/Features'


export interface ExperimentInfo {
  allDatasetOptions?: (null)[] | null;
  features?: (Features)[] | null;
  workbook: Workbook;
  workbookExecState: string;
}