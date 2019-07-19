import {
    Experiment,
    ExperimentInfo,
    DefaultResponse,
    DefaultListOfItems,
    Atlas
} from '@app/models';

export namespace experiment {
    export namespace info {
        export interface Response extends DefaultResponse {
            experiment: Experiment;
            experimentInfo: ExperimentInfo;
            atlas: Atlas;
        }
    }
}