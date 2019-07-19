import {
    Experiment,
    ExperimentInfo,
    DefaultResponse,
    DefaultListOfItems,
    Atlas
} from '@app/models';

import { MetricsResult } from './MetricsResult';
import { HyperParamResult } from './HyperParamResult';
import { ConsoleResult } from './ConsoleResult';
import { ExperimentFeature } from './ExperimentFeature';
import { Tasks } from './Tasks';


export namespace experiment {
    export namespace info {
        export interface Response extends DefaultResponse {
            experiment: Experiment;
            experimentInfo: ExperimentInfo;
            atlas: Atlas;
        }
    }
    export namespace featureProgress {
        export interface Response extends DefaultResponse {
            consoleResult: ConsoleResult;
            experimentFeature: ExperimentFeature;
            hyperParamResult: HyperParamResult;
            metricsResult: MetricsResult;
            tasks: Tasks;
        }
    }
    export namespace featureProgressConsolePart {
        export interface Response extends DefaultResponse {
            console: string;
            exitCode: number;
            isOk: boolean;
            ok: boolean;
        }
    }

    export namespace featureProgressPart {
        export interface Response extends DefaultResponse {
            consoleResult: ConsoleResult;
            experimentFeature: ExperimentFeature;
            hyperParamResult: HyperParamResult;
            metricsResult: MetricsResult;
            tasks: Tasks;
        }
    }
}