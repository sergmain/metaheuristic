export namespace ExperimentFeatureProgressConsolePartResponse{
	export interface Response {
	  items?: (ItemsEntity)[] | null;
	}
	export interface ItemsEntity {
	  exitCode: number;
	  isOk: boolean;
	  console: string;
	  ok: boolean;
	}
}