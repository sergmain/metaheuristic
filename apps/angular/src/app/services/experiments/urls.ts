import { environment } from 'environments/environment';
import jsonToUrlParams from '@app/helpers/jsonToUrlParams'

let base = environment.baseUrl + 'launchpad/experiment';

let urls = {
    experiments: {
        get: page => `${base}/experiments?page=${page}`
    },
    experiment: {
        get: id => `${base}/experiment/${id}`,
        info: (id) => `${base}/experiment-info/${id}`,
        edit: (id) => `${base}/experiment-edit/${id}`,
        addCommit: () => `${base}/experiment-add-commit`,
        editCommit: () => `${base}/experiment-edit-commit`,
        deleteCommit: (data) => `${base}/experiment-delete-commit?${jsonToUrlParams(data)}`,
        cloneCommit: (data) => `${base}/experiment-clone-commit?${jsonToUrlParams(data)}`,

        featurePlotDataPart: (experimentId, featureId, params, paramsAxis) => `${base}/experiment-feature-plot-data-part/${experimentId}/${featureId}/${params}/${paramsAxis}/part`,
        featureProgressConsolePart: taskId => `${base}/experiment-feature-progress-console-part/${taskId}`,
        featureProgressConsole: taskId => `${base}/experiment-feature-progress-console/${taskId}`,
        featureProgressPart: (experimentId, featureId, params) => `${base}/experiment-feature-progress-part/${experimentId}/${featureId}/${params}/part`,
        featureProgress: (experimentId, featureId) => `${base}/experiment-feature-progress/${experimentId}/${featureId}`,

        metadataAddCommit: (experimentId,data) => `${base}/experiment-metadata-add-commit/${experimentId}?${jsonToUrlParams(data)}`,
        metadataEditCommit: (experimentId, data) => `${base}/experiment-metadata-edit-commit/${experimentId}?${jsonToUrlParams(data)}`,
        metadataDeleteCommit: (experimentId, id) => `${base}/experiment-metadata-delete-commit/${experimentId}/${id}`,
        metadataDefaultAddCommit: (experimentId) => `${base}/experiment-metadata-default-add-commit/${experimentId}`,

        snippetAddCommit: (id,data) => `${base}/experiment-snippet-add-commit/${id}?${jsonToUrlParams(data)}`,
        snippetDeleteCommit: (experimentId, id) => `${base}/experiment-snippet-delete-commit/${experimentId}/${id}`,

        taskRerun: (taskId) => `${base}/task-rerun/${taskId}`
    }
}

export { urls }


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