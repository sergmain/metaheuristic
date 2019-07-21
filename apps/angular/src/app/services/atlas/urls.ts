import { environment } from '@src/environments/environment';
import { jsonToUrlParams as toURL } from '@app/helpers/jsonToUrlParams';

const base: string = environment.baseUrl + 'launchpad/atlas';

interface URLS {
    experiments: {
        get(page: number): string;
    };
    experiment: {
        get(id: string): string;
        info(id: string): string
        // edit(id: string): string
        // addCommit(): string
        // editCommit(): string
        deleteCommit(data: any): string
        // cloneCommit(data: any): string
        featurePlotDataPart(
            atlasId: string,
            experimentId: string,
            featureId: string,
            params: string,
            paramsAxis: string): string
        featureProgressConsolePart(
            atlasId: string,
            taskId: string): string
        // featureProgressConsole(
        //     taskId: string): string
        featureProgressPart(
            atlasId: string,
            experimentId: string,
            featureId: string,
            params: string): string
        featureProgress(
            atlasId: string,
            experimentId: string,
            featureId: string): string
        // metadataAddCommit(
        //     experimentId: string,
        //     data: any): string
        // metadataEditCommit(
        //     experimentId: string,
        //     data: any): string
        // metadataDeleteCommit(
        //     experimentId: string,
        //     id: string): string
        // metadataDefaultAddCommit(
        //     experimentId: string): string
        // snippetAddCommit(
        //     id: string,
        //     data: any): string
        // snippetDeleteCommit(
        //     experimentId: string,
        //     id: string): string
        // taskRerun(
        //     taskId: string): string
    };
}

const urls: URLS = {
    experiments: {
        get: (page: number): string => `${base}/atlas-experiments?page=${page}`
    },
    experiment: {
        get: (id: string): string => `${base}/experiment/${id}`,
        info: (id: string): string => `${base}/atlas-experiment-info/${id}`,
        // edit: (id: string): string => `${base}/experiment-edit/${id}`,
        // addCommit: (): string => `${base}/experiment-add-commit`,
        // editCommit: (): string => `${base}/experiment-edit-commit`,
        deleteCommit: (data: any): string => `${base}/atlas-experiment-delete-commit?${toURL(data)}`,
        // cloneCommit: (data: any): string => `${base}/experiment-clone-commit?${toURL(data)}`,

        featurePlotDataPart: (atlasId: string, experimentId: string, featureId: string, params: string, paramsAxis: string): string => `${base}/atlas-experiment-feature-plot-data-part/${atlasId}/${experimentId}/${featureId}/${params}/${paramsAxis}/part`,
        featureProgressConsolePart: (atlasId: string, taskId: string): string => `${base}/atlas-experiment-feature-progress-console-part/${atlasId}/${taskId}`,
        // featureProgressConsole: (taskId: string): string => `${base}/experiment-feature-progress-console/${taskId}`,
        featureProgressPart: (atlasId: string, experimentId: string, featureId: string, params: string): string => `${base}/atlas-experiment-feature-progress-part/${atlasId}/${experimentId}/${featureId}/${params}/part`,
        featureProgress: (atlasId: string, experimentId: string, featureId: string): string => `${base}/atlas-experiment-feature-progress/${atlasId}/${experimentId}/${featureId}`,

        // metadataAddCommit: (experimentId: string, data: any): string => `${base}/experiment-metadata-add-commit/${experimentId}?${toURL(data)}`,
        // metadataEditCommit: (experimentId: string, data: any): string => `${base}/experiment-metadata-edit-commit/${experimentId}?${toURL(data)}`,
        // metadataDeleteCommit: (experimentId: string, id: string): string => `${base}/experiment-metadata-delete-commit/${experimentId}/${id}`,
        // metadataDefaultAddCommit: (experimentId: string): string => `${base}/experiment-metadata-default-add-commit/${experimentId}`,

        // snippetAddCommit: (id: string, data: any): string => `${base}/experiment-snippet-add-commit/${id}?${toURL(data)}`,
        // snippetDeleteCommit: (experimentId: string, id: string): string => `${base}/experiment-snippet-delete-commit/${experimentId}/${id}`,

        // taskRerun: (taskId: string): string => `${base}/task-rerun/${taskId}`
    }
};

export { urls };