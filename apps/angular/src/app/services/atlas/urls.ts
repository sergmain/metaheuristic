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
            params: any,
            page: any): string
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
        get: (page: number): string =>
            `${base}/atlas-experiments?page=${page}`
    },
    experiment: {
        get: (id: string): string =>
            `${base}/experiment/${id}`,

        info: (id: string): string =>
            `${base}/atlas-experiment-info/${id}`,

        deleteCommit: (data: any): string =>
            `${base}/atlas-experiment-delete-commit?${toURL(data)}`,

        featurePlotDataPart: (atlasId: string, experimentId: string, featureId: string, params: string, paramsAxis: string): string =>
            `${base}/atlas-experiment-feature-plot-data-part/${atlasId}/${experimentId}/${featureId}/${params}/${paramsAxis}/part`,

        featureProgressConsolePart: (atlasId: string, taskId: string): string =>
            `${base}/atlas-experiment-feature-progress-console-part/${atlasId}/${taskId}`,

        featureProgressPart: (atlasId: string, experimentId: string, featureId: string, params: string, page: any): string =>
            `${base}/atlas-experiment-feature-progress-part/${atlasId}/${experimentId}/${featureId}/${params}/part?${toURL(page)}`,

        featureProgress: (atlasId: string, experimentId: string, featureId: string): string =>
            `${base}/atlas-experiment-feature-progress/${atlasId}/${experimentId}/${featureId}`,
    }
};

export { urls };