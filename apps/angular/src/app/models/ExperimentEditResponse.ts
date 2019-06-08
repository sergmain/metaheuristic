export namespace ExperimentEditResponse {
    export interface Response {
        errorMessages ? : null;
        infoMessages ? : null;
        hyperParams: HyperParams;
        simpleExperiment: SimpleExperiment;
        snippetResult: SnippetResult;
    }
    export interface HyperParams {
        items ? : (ItemsEntity)[] | null;
    }
    export interface ItemsEntity {
        id ? : number;
        version ? : number;
        key ? : string;
        values ? : string;
        newValues ? : string;
        variants ? : number;
    }
    export interface SimpleExperiment {
        name: string;
        description: string;
        code: string;
        seed: number;
        id: number;
    }

    export interface SnippetResult {
        selectOptions ? : (SelectOptionsEntity)[] | null;
        snippets ? : (null)[] | null;
    }
    export interface SelectOptionsEntity {
        value: string;
        desc: string;
    }

}