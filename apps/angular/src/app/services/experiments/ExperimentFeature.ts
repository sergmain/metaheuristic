export interface ExperimentFeature {
    id: number;
    version: number;
    resourceCodes: string;
    checksumIdCodes: string;
    execStatus: number;
    experimentId: number;
    maxValue: number;
    execStatusAsString: string;
}