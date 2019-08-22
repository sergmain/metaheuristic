import {
    ExperimentInfo,
    DefaultResponse,
    DefaultListOfItems
} from '@app/models';

import { Atlas } from './Atlas';
import { MetricsResult } from './MetricsResult';
import { HyperParamResult } from './HyperParamResult';
import { ConsoleResult } from './ConsoleResult';
import { ExperimentFeature } from './ExperimentFeature';
import { Tasks } from './Tasks';
import { Experiment } from './Experiment';

export interface ListOfItems extends DefaultListOfItems {
    content: ExperimentItem[];
}

export interface ExperimentItem extends DefaultResponse {
    experiment: Experiment;
}

export namespace response {
    export namespace experiments {
        export interface Get extends DefaultResponse {
            items: ListOfItems;
        }
    }


    export namespace experiment {
        export interface Info extends DefaultResponse {
            experiment: Experiment;
            experimentInfo: ExperimentInfo;
            atlas: Atlas;
        }
        export interface FeatureProgress extends DefaultResponse {
            consoleResult: ConsoleResult;
            experimentFeature: ExperimentFeature;
            hyperParamResult: HyperParamResult;
            metricsResult: MetricsResult;
            tasks: Tasks;
        }
        export interface FeatureProgressConsolePart extends DefaultResponse {
            console: string;
            exitCode: number;
            isOk: boolean;
            ok: boolean;
        }

        export interface FeatureProgressPart extends DefaultResponse {
            consoleResult: ConsoleResult;
            experimentFeature: ExperimentFeature;
            hyperParamResult: HyperParamResult;
            metricsResult: MetricsResult;
            tasks: Tasks;
        }
    }
}