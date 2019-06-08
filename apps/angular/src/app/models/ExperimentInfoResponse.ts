export namespace ExperimentInfoResponse{
	export interface Response {
	  errorMessages?: null;
	  infoMessages?: null;
	  experiment: Experiment;
	  experimentInfo: ExperimentInfo;
	}
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
	  hyperParams?: (HyperParamsEntity)[] | null;
	  allTaskProduced: boolean;
	  featureProduced: boolean;
	  hyperParamsAsMap: any|null;
	}
	export interface HyperParamsEntity {
	  id: number;
	  version: number;
	  key: string;
	  values: string;
	  variants: number;
	}

	export interface ExperimentInfo {
	  allDatasetOptions?: (null)[] | null;
	  features?: (FeaturesEntity)[] | null;
	  workbook: Workbook;
	  workbookExecState: string;
	}
	export interface FeaturesEntity {
	  id: number;
	  version: number;
	  resourceCodes: string;
	  checksumIdCodes: string;
	  execStatus: number;
	  experimentId: number;
	  maxValue: number;
	}
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
}