import { environment } from 'environments/environment';
import { jsonToUrlParams as toURL } from '@app/helpers/jsonToUrlParams';

const base: string = environment.baseUrl + 'launchpad/atlas';

const urls: any = {
    experiments: {
        get: (page: number): string => `${base}/atlas-experiments?page=${page}`
    },
    experiment: {
        info: (id: string): string => `${base}/atlas-experiment-info/${id}`,
    }
};

export { urls }

// @GetMapping("/atlas-experiments")
// public AtlasData.AtlasSimpleExperiments init(@PageableDefault(size = 5) Pageable pageable) {
//     return atlasService.getAtlasExperiments(pageable);
// }

// @GetMapping(value = "/atlas-experiment-info/{id}")
// public AtlasData.ExperimentInfoExtended info(@PathVariable Long id) {
//     return atlasTopLevelService.getExperimentInfoExtended(id);
// }

// @PostMapping("/atlas-experiment-delete-commit")
// public OperationStatusRest deleteCommit(Long id) {
//     return atlasTopLevelService.atlasDeleteCommit(id);
// }

// @GetMapping(value = "/atlas-experiment-feature-progress/{atlasId}/{experimentId}/{featureId}")
// public AtlasData.ExperimentFeatureExtendedResult getFeatures(
//         @PathVariable Long atlasId,@PathVariable Long experimentId, @PathVariable Long featureId) {
//     return atlasTopLevelService.getExperimentFeatureExtended(atlasId, experimentId, featureId);
// }

// @PostMapping("/atlas-experiment-feature-plot-data-part/{atlasId}/{experimentId}/{featureId}/{params}/{paramsAxis}/part")
// @ResponseBody
// public AtlasData.PlotData getPlotData(
//         @PathVariable Long atlasId,
//         @PathVariable Long experimentId, @PathVariable Long featureId,
//         @PathVariable String[] params, @PathVariable String[] paramsAxis) {
//     return atlasTopLevelService.getPlotData(atlasId, experimentId, featureId, params, paramsAxis);
// }

// @PostMapping("/atlas-experiment-feature-progress-console-part/{atlasId}/{taskId}")
// public AtlasData.ConsoleResult getTasksConsolePart(@PathVariable(name = "atlasId") Long atlasId, @PathVariable(name = "taskId") Long taskId ) {
//     return atlasTopLevelService.getTasksConsolePart(atlasId, taskId);
// }

// @PostMapping("/atlas-experiment-feature-progress-part/{atlasId}/{experimentId}/{featureId}/{params}/part")
// public AtlasData.ExperimentFeatureExtendedResult getFeatureProgressPart(@PathVariable Long atlasId, @PathVariable Long experimentId, @PathVariable Long featureId, @PathVariable String[] params, @SuppressWarnings("DefaultAnnotationParam") @PageableDefault(size = 10) Pageable pageable) {
//     return atlasTopLevelService.getFeatureProgressPart(atlasId, experimentId, featureId, params, pageable);
// }