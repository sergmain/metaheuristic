/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.ai.launchpad.experiment;

import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.beans.Experiment;
import ai.metaheuristic.ai.launchpad.context.LaunchpadContextService;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeController;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeTopLevelService;
import ai.metaheuristic.ai.launchpad.exec_context.ExecContextCache;
import ai.metaheuristic.ai.launchpad.exec_context.ExecContextService;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.api.launchpad.ExecContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;

/**
 * User: Serg
 * Date: 12.06.2017
 * Time: 20:22
 */
@SuppressWarnings("Duplicates")
@Controller
@RequestMapping("/launchpad/experiment")
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
public class ExperimentController {

    private static final String REDIRECT_LAUNCHPAD_EXPERIMENTS = "redirect:/launchpad/experiment/experiments";
    private final ExperimentTopLevelService experimentTopLevelService;
    private final ExecContextService execContextService;
    private final ExecContextCache execContextCache;
    private final ExperimentCache experimentCache;
    private final SourceCodeTopLevelService sourceCodeTopLevelService;
    private final LaunchpadContextService launchpadContextService;

    @GetMapping("/experiments")
    public String getExperiments(Model model, @PageableDefault(size = 5) Pageable pageable,
                       @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
                       @ModelAttribute("errorMessage") final ArrayList<String> errorMessage) {
        ExperimentApiData.ExperimentsResult experiments = experimentTopLevelService.getExperiments(pageable);
        ControllerUtils.addMessagesToModel(model, experiments);
        model.addAttribute("result", experiments);
        return "launchpad/experiment/experiments";
    }

    // for AJAX
    @PostMapping("/experiments-part")
    public String getExperimentsAjax(Model model, @PageableDefault(size = 5) Pageable pageable) {
        ExperimentApiData.ExperimentsResult experiments = experimentTopLevelService.getExperiments(pageable);
        model.addAttribute("result", experiments);
        return "launchpad/experiment/experiments :: table";
    }

    @PostMapping("/experiment-feature-plot-data-part/{experimentId}/{featureId}/{params}/{paramsAxis}/part")
    public @ResponseBody
    ExperimentApiData.PlotData getPlotData(
            @PathVariable Long experimentId, @PathVariable Long featureId,
            @PathVariable String[] params, @PathVariable String[] paramsAxis) {
        return experimentTopLevelService.getPlotData(experimentId, featureId, params, paramsAxis);
    }

    @PostMapping("/experiment-feature-progress-console-part/{taskId}")
    public String getTasksConsolePart(Model model, @PathVariable(name="taskId") Long taskId) {
        ExperimentApiData.ConsoleResult result = experimentTopLevelService.getTasksConsolePart(taskId);
        model.addAttribute("consoleResult", result);
        return "launchpad/experiment/experiment-feature-progress :: fragment-console-table";
    }

    @PostMapping("/experiment-feature-progress-part/{experimentId}/{featureId}/{params}/part")
    public String getFeatureProgressPart(Model model, @PathVariable Long experimentId, @PathVariable Long featureId, @PathVariable String[] params, @SuppressWarnings("DefaultAnnotationParam") @PageableDefault(size = 10) Pageable pageable) {
        ExperimentApiData.ExperimentFeatureExtendedResult experimentProgressResult =
                experimentTopLevelService.getFeatureProgressPart(experimentId, featureId, params, pageable);

//        model.addAttribute("metrics", experimentProgressResult.metricsResult);
//        model.addAttribute("params", experimentProgressResult.hyperParamResult);
        model.addAttribute("result", experimentProgressResult.tasksResult);
        model.addAttribute("experiment", experimentProgressResult.experiment);
        model.addAttribute("feature", experimentProgressResult.experimentFeature);
        model.addAttribute("consoleResult", experimentProgressResult.consoleResult);

        return "launchpad/experiment/experiment-feature-progress :: fragment-table";
    }

    @GetMapping(value = "/experiment-feature-progress/{experimentId}/{featureId}")
    public String getFeatures(Model model, @PathVariable Long experimentId, @PathVariable Long featureId, final RedirectAttributes redirectAttributes ) {
        ExperimentApiData.ExperimentFeatureExtendedResult experimentProgressResult =
            experimentTopLevelService.getExperimentFeatureExtended(experimentId, featureId);
        if (experimentProgressResult.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", experimentProgressResult.errorMessages);
            return REDIRECT_LAUNCHPAD_EXPERIMENTS;
        }
        model.addAttribute("metrics", experimentProgressResult.metricsResult);
        model.addAttribute("params", experimentProgressResult.hyperParamResult);
        model.addAttribute("result", experimentProgressResult.tasksResult);
        model.addAttribute("experiment", experimentProgressResult.experiment);
        model.addAttribute("feature", experimentProgressResult.experimentFeature);
        model.addAttribute("consoleResult", experimentProgressResult.consoleResult);

        return "launchpad/experiment/experiment-feature-progress";
    }

    @GetMapping(value = "/experiment-add")
    public String add(@ModelAttribute("experiment") ExperimentApiData.ExperimentData experiment) {
        experiment.setSeed(1);
        return "launchpad/experiment/experiment-add-form";
    }

    @GetMapping(value = "/experiment-info/{id}")
    public String info(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, @ModelAttribute("errorMessage") final String errorMessage ) {
        ExperimentApiData.ExperimentInfoExtendedResult result = experimentTopLevelService.getExperimentInfo(id);
        if (result.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.errorMessages);
            return REDIRECT_LAUNCHPAD_EXPERIMENTS;
        }

        if (result.isInfoMessages()) {
            model.addAttribute("infoMessages", result.infoMessages);
        }


        model.addAttribute("experiment", result.experiment);
        model.addAttribute("experimentResult", result.experimentInfo);
        model.addAttribute("progress", result.progress);
        return "launchpad/experiment/experiment-info";
    }

    @GetMapping(value = "/experiment-edit/{id}")
    public String edit(@PathVariable Long id, Model model, @ModelAttribute("errorMessage") final String errorMessage, final RedirectAttributes redirectAttributes) {
        ExperimentApiData.ExperimentsEditResult r = experimentTopLevelService.editExperiment(id);
        if (r.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", r.errorMessages);
            return REDIRECT_LAUNCHPAD_EXPERIMENTS;
        }

        model.addAttribute("hyperParams", r.hyperParams);
        model.addAttribute("simpleExperiment", r.simpleExperiment);
        model.addAttribute("snippetResult", r.snippetResult);
        return "launchpad/experiment/experiment-edit-form";
    }

    @GetMapping("/exec-context-target-exec-state/{experimentId}/{state}/{id}")
    public String execContextTargetExecState(@PathVariable Long experimentId, @PathVariable String state,
                                          @PathVariable Long id, final RedirectAttributes redirectAttributes, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = sourceCodeTopLevelService.changeExecContextState(state, id, context);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
            return SourceCodeController.REDIRECT_LAUNCHPAD_SOURCE_CODES;
        }
        return "redirect:/launchpad/experiment/experiment-info/" + experimentId;
    }

    @PostMapping(value = "/experiment-upload-from-file")
    public String uploadExperiment(final MultipartFile file, final RedirectAttributes redirectAttributes) {
        OperationStatusRest operationStatusRest = experimentTopLevelService.uploadExperiment(file);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
        }
        return REDIRECT_LAUNCHPAD_EXPERIMENTS;
    }

    @PostMapping("/experiment-add-form-commit")
    public String addFormCommit(ExperimentApiData.ExperimentData experiment, final RedirectAttributes redirectAttributes) {
        OperationStatusRest status = experimentTopLevelService.addExperimentCommit(experiment);
        if (status.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", status.errorMessages);
            return "launchpad/experiment/experiment-add-form";
        }
        return REDIRECT_LAUNCHPAD_EXPERIMENTS;
    }

    @PostMapping("/experiment-edit-form-commit")
    public String editFormCommit(ExperimentApiData.SimpleExperiment simpleExperiment, final RedirectAttributes redirectAttributes) {
        OperationStatusRest status = experimentTopLevelService.editExperimentCommit(simpleExperiment);
        if (status.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", status.errorMessages);
        }
        return "redirect:/launchpad/experiment/experiment-edit/" + simpleExperiment.getId();
    }

    @PostMapping("/experiment-metadata-add-commit/{id}")
    public String metadataAddCommit(@PathVariable Long id, String key, String value, final RedirectAttributes redirectAttributes) {
        OperationStatusRest status = experimentTopLevelService.metadataAddCommit(id, key, value);
        if (status.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", status.errorMessages);
        }
        return "redirect:/launchpad/experiment/experiment-edit/"+id;
    }

    @PostMapping("/experiment-metadata-edit-commit/{id}")
    public String metadataEditCommit(@PathVariable Long id, String key, String value, final RedirectAttributes redirectAttributes) {
        OperationStatusRest status = experimentTopLevelService.metadataEditCommit(id, key, value);
        if (status.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", status.errorMessages);
        }
        return "redirect:/launchpad/experiment/experiment-edit/"+id;
    }

    @PostMapping("/experiment-snippet-add-commit/{id}")
    public String snippetAddCommit(@PathVariable Long id, String code, final RedirectAttributes redirectAttributes) {
        OperationStatusRest status = experimentTopLevelService.snippetAddCommit(id, code);
        if (status.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", status.errorMessages);
            return REDIRECT_LAUNCHPAD_EXPERIMENTS;
        }
        return "redirect:/launchpad/experiment/experiment-edit/"+id;
    }

    @GetMapping("/experiment-metadata-delete-commit/{experimentId}/{key}")
    public String metadataDeleteCommit(@PathVariable Long experimentId, @PathVariable String key, final RedirectAttributes redirectAttributes) {
        OperationStatusRest status = experimentTopLevelService.metadataDeleteCommit(experimentId, key);
        if (status.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", status.errorMessages);
        }
        return "redirect:/launchpad/experiment/experiment-edit/" + experimentId;
    }

    @GetMapping("/experiment-metadata-default-add-commit/{experimentId}")
    public String metadataDefaultAddCommit(@PathVariable Long experimentId, final RedirectAttributes redirectAttributes) {
        OperationStatusRest status = experimentTopLevelService.metadataDefaultAddCommit(experimentId);
        if (status.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", status.errorMessages);
        }
        return "redirect:/launchpad/experiment/experiment-edit/" + experimentId;
    }

    @GetMapping("/experiment-snippet-delete-commit/{experimentId}/{snippetCode}")
    public String snippetDeleteCommit(@PathVariable Long experimentId, @PathVariable String snippetCode, final RedirectAttributes redirectAttributes) {
        OperationStatusRest status = experimentTopLevelService.snippetDeleteCommit(experimentId, snippetCode);
        if (status.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", status.errorMessages);
        }
        return "redirect:/launchpad/experiment/experiment-edit/" + experimentId;
    }

    @GetMapping("/experiment-snippet-delete-by-type-commit/{experimentId}/{snippetType}")
    public String snippetDeleteByTypeCommit(@PathVariable Long experimentId, @PathVariable String snippetType, final RedirectAttributes redirectAttributes) {
        OperationStatusRest status = experimentTopLevelService.snippetDeleteByTypeCommit(experimentId, snippetType);
        if (status.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", status.errorMessages);
        }
        return "redirect:/launchpad/experiment/experiment-edit/" + experimentId;
    }

    @GetMapping("/experiment-delete/{id}")
    public String delete(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        ExperimentApiData.ExperimentResult result = experimentTopLevelService.getExperimentWithoutProcessing(id);
        if (result.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.errorMessages);
            return REDIRECT_LAUNCHPAD_EXPERIMENTS;
        }

        model.addAttribute("experiment", result.experiment);
        model.addAttribute("params", result.params);
        return "launchpad/experiment/experiment-delete";
    }

    @PostMapping("/experiment-delete-commit")
    public String deleteCommit(Long id, final RedirectAttributes redirectAttributes, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "#285.260 experiment wasn't found, experimentId: " + id);
            return REDIRECT_LAUNCHPAD_EXPERIMENTS;
        }
        if (experiment.execContextId !=null) {
            ExecContext wb = execContextCache.findById(experiment.execContextId);
            if (wb != null) {
                OperationStatusRest operationStatusRest = sourceCodeTopLevelService.deleteExecContextById(experiment.execContextId, context);
                if (operationStatusRest.isErrorMessages()) {
                    redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
                    return REDIRECT_LAUNCHPAD_EXPERIMENTS;
                }
            }
        }
        OperationStatusRest status = experimentTopLevelService.experimentDeleteCommit(id);
        if (status.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", status.errorMessages);
        }
        return REDIRECT_LAUNCHPAD_EXPERIMENTS;
    }

    @PostMapping("/experiment-clone-commit")
    public String experimentCloneCommit(Long id, final RedirectAttributes redirectAttributes) {
        OperationStatusRest status = experimentTopLevelService.experimentCloneCommit(id);
        if (status.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", status.errorMessages);
        }
        return REDIRECT_LAUNCHPAD_EXPERIMENTS;
    }

    @GetMapping(value = "/experiment-to-atlas/{id}")
    public String toAtlas(@PathVariable Long id, final RedirectAttributes redirectAttributes) {
        OperationStatusRest status = experimentTopLevelService.toAtlas(id);
        if (status.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", status.errorMessages);
        }
        return "redirect:/launchpad/experiment/experiment-info/"+id;
    }

    @PostMapping("/task-rerun/{taskId}")
    public @ResponseBody boolean rerunTask(@PathVariable Long taskId) {
        return execContextService.resetTask(taskId).status == EnumsApi.OperationStatus.OK;
    }

    @GetMapping("/task-reset-all-broken/{execContextId}/{experimentId}")
    public String rerunBrokenTasks(
            @PathVariable Long execContextId, @PathVariable Long experimentId, final RedirectAttributes redirectAttributes) {
        OperationStatusRest status = execContextService.resetBrokenTasks(execContextId);
        if (status.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", status.errorMessages);
        }
        return "redirect:/launchpad/experiment/experiment-info/"+experimentId;
    }

    @PostMapping("/bind-experiment-to-source-code-with-resource")
    public String bindExperimentToSourceCodeWithResource(Long experimentId, String experimentCode, String resourcePoolCode,
                                                         final RedirectAttributes redirectAttributes, Authentication authentication) {
        LaunchpadContext context = launchpadContextService.getContext(authentication);
        OperationStatusRest status = experimentTopLevelService.bindExperimentToSourceCodeWithResource(experimentCode, resourcePoolCode, context);
        if (status.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", status.errorMessages);
        }
        return "redirect:/launchpad/experiment/experiment-info/"+experimentId;
    }

}
