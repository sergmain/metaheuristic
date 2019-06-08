export namespace ExperimentEditCommitResponse {
    export interface Response {
        errorMessages ? : null;
        infoMessages ? : null;
        experiment: Experiment;
    }
    export interface Experiment {
        id: number;
        version: number;
        workbookId ? : null;
        name: string;
        description: string;
        code: string;
        seed: number;
        createdOn: number;
        numberOfTask: number;
        hyperParams ? : (HyperParamsEntity)[] | null;
        allTaskProduced: boolean;
        featureProduced: boolean;
        hyperParamsAsMap: HyperParamsAsMap;
    }
    export interface HyperParamsEntity {
        id: number;
        version: number;
        key: string;
        values: string;
        variants: number;
    }
    export interface HyperParamsAsMap {
        epoch: Epoch;
        RNN: RNN;
        activation: Activation;
        optimizer: Optimizer;
        batch_size: BatchSize;
        time_steps: TimeSteps;
        metrics_functions: MetricsFunctions;
        test: Test;
    }
    export interface Epoch {

    }
    export interface RNN {

    }
    export interface Activation {

    }
    export interface Optimizer {

    }
    export interface BatchSize {

    }
    export interface TimeSteps {

    }
    export interface MetricsFunctions {

    }
    export interface Test {}

}