import {
    Snippet,
    Experiment,
    DefaultResponse,
    DefaultListOfItems
} from '@app/models';

export interface ListOfItems extends DefaultListOfItems {
    content: ExperimentItem[];
}

export interface ExperimentItem extends DefaultResponse {
    experiment: Experiment;
}

export interface SimpleExperiment {
    id: number;
    name: string;
    description: string;
    code: string;
    seed: number;
}



export interface HyperParams {
    items: HyperParam[];
}

export interface HyperParam {
    id ? : number;
    version ? : number;
    key ? : string;
    values ? : string;
    newValues ? : string;
    variants ? : number;
}

export interface SnippetResult {
    selectOptions: SelectOptionsEntity[];
    snippets: Snippet[];
}

export interface Snippet {
    experimentId: number;
    id: number;
    snippetCode: string;
    type: string;
    version: number;
}

export interface SelectOptionsEntity {
    value: string;
    desc: string;
}


export namespace experiments {
    export namespace get {
        export interface Response extends DefaultResponse {
            items: ListOfItems;
        }
    }
}

export namespace experiment {
    export namespace get {
        export interface Response extends DefaultResponse {
            Experiment: Experiment;
        }
    }
    // export namespace info {
    //     export interface Response {

    //     }
    // }
    export namespace edit {
        export interface Response {
            simpleExperiment: SimpleExperiment;
            hyperParams: HyperParams;
            snippetResult: SnippetResult;
        }
    }
    // export namespace addCommit {
    //     export interface Response {

    //     }
    // }
    // export namespace editCommit {
    //     export interface Response {

    //     }
    // }
    // export namespace deleteCommit {
    //     export interface Response {

    //     }
    // }
    // export namespace cloneCommit {
    //     export interface Response {

    //     }
    // }
    // export namespace featurePlotDataPart {
    //     export interface Response {

    //     }
    // }
    // export namespace featureProgressPart {
    //     export interface Response {

    //     }
    // }
    // export namespace featureProgress {
    //     export interface Response {

    //     }
    // }
    // export namespace featureProgressConsole {
    //     export interface Response {

    //     }
    // }
    // export namespace featureProgressConsolePart {
    //     export interface Response {

    //     }
    // }
    // export namespace taskRerun {
    //     export interface Response {

    //     }
    // }
    // export namespace metadataAddCommit {
    //     export interface Response {

    //     }
    // }
    // export namespace metadataEditCommit {
    //     export interface Response {

    //     }
    // }
    // export namespace metadataDeleteCommit {
    //     export interface Response {

    //     }
    // }
    // export namespace metadataDefaultAddCommit {
    //     export interface Response {

    //     }
    // }
    // export namespace snippetAddCommit {
    //     export interface Response {

    //     }
    // }
    // export namespace snippetDeleteCommit {
    //     export interface Response {

    //     }
    // }
}