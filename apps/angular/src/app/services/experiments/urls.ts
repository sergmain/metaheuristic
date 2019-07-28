import jsonToUrlParams from '@app/helpers/jsonToUrlParams';
import { environment } from '@src/environments/environment';

const base: string = environment.baseUrl + 'launchpad/experiment';

const experiments: any = {
    get: (page: number): string => `${base}/experiments?page=${page}`
}
const experiment: any = {
    get: (id: string): string => `${base}/experiment/${id}`,
    info: (id: string): string => `${base}/experiment-info/${id}`,
    edit: (id: string): string => `${base}/experiment-edit/${id}`,
    addCommit: (): string => `${base}/experiment-add-commit`,
    editCommit: (): string => `${base}/experiment-edit-commit`,
    deleteCommit: (data: any): string => `${base}/experiment-delete-commit?${jsonToUrlParams(data)}`,
    cloneCommit: (data: any): string => `${base}/experiment-clone-commit?${jsonToUrlParams(data)}`,

    featurePlotDataPart: (experimentId: string, featureId: string, params: string, paramsAxis: string): string => `${base}/experiment-feature-plot-data-part/${experimentId}/${featureId}/${params}/${paramsAxis}/part`,
    featureProgressConsolePart: (taskId: string): string => `${base}/experiment-feature-progress-console-part/${taskId}`,
    featureProgressConsole: (taskId: string): string => `${base}/experiment-feature-progress-console/${taskId}`,
    featureProgressPart: (experimentId: string, featureId: string, params: string): string => `${base}/experiment-feature-progress-part/${experimentId}/${featureId}/${params}/part`,
    featureProgress: (experimentId: string, featureId: string): string => `${base}/experiment-feature-progress/${experimentId}/${featureId}`,

    metadataAddCommit: (experimentId: string, data: any): string => `${base}/experiment-metadata-add-commit/${experimentId}?${jsonToUrlParams(data)}`,
    metadataEditCommit: (experimentId: string, data: any): string => `${base}/experiment-metadata-edit-commit/${experimentId}?${jsonToUrlParams(data)}`,
    metadataDeleteCommit: (experimentId: string, id: string): string => `${base}/experiment-metadata-delete-commit/${experimentId}/${id}`,
    metadataDefaultAddCommit: (experimentId: string): string => `${base}/experiment-metadata-default-add-commit/${experimentId}`,

    snippetAddCommit: (id: string, data: any): string => `${base}/experiment-snippet-add-commit/${id}?${jsonToUrlParams(data)}`,
    snippetDeleteCommit: (experimentId: string, id: string): string => `${base}/experiment-snippet-delete-commit/${experimentId}/${id}`,

    taskRerun: (taskId: string): string => `${base}/task-rerun/${taskId}`,
    toAtlas: (id: string): string => `${base}/experiment-to-atlas/${id}`
}

const urls: any = { experiment, experiments }

export { experiment, experiments, urls };


// GET ("/experiments")
// GET ("/experiment/{id}")
// POST("/experiment-feature-plot-data-part/{experimentId}/{featureId}/{params}/{paramsAxis}/part")
// POST("/experiment-feature-progress-console-part/{taskId}")
// GET ("/experiment-feature-progress-console/{taskId}")
// POST("/experiment-feature-progress-part/{experimentId}/{featureId}/{params}/part")
// GET ("/experiment-feature-progress/{experimentId}/{featureId}")
// GET ("/experiment-info/{id}")
// GET ("/experiment-edit/{id}")
// POST("/experiment-add-commit")
// POST("/experiment-edit-commit")
// POST("/experiment-metadata-add-commit/{id}")
// POST("/experiment-metadata-edit-commit/{id}")
// POST("/experiment-snippet-add-commit/{id}")
// GET ("/experiment-metadata-delete-commit/{experimentId}/{id}")
// GET ("/experiment-metadata-default-add-commit/{experimentId}")
// GET ("/experiment-snippet-delete-commit/{experimentId}/{id}")
// POST("/experiment-delete-commit")
// POST("/experiment-clone-commit")
// POST("/task-rerun/{taskId}")