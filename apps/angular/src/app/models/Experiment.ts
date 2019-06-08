export interface Experiment {
  id: number;
  version: number;
  workbookId: number;
  name: string;
  description: string;
  code: string;
  seed: number;
  createdOn: number;
  numberOfTask: number;
  hyperParams?: (any)[] | null;
  featureProduced: boolean;
  allTaskProduced: boolean;
  hyperParamsAsMap: any;
}