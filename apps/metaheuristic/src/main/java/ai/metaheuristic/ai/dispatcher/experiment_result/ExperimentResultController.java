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

package ai.metaheuristic.ai.dispatcher.experiment_result;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.data.ExperimentResultData;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;

@SuppressWarnings("Duplicates")
@Controller
@RequestMapping("/dispatcher/ai/experiment-result")
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
public class ExperimentResultController {

    private static final String REDIRECT_DISPATCHER_EXPERIMENT_RESULT_EXPERIMENT_RESULTS = "redirect:/dispatcher/ai/experiment-result/experiment-results";
    private final ExperimentResultService experimentResultService;
    private final ExperimentResultTopLevelService experimentResultTopLevelService;
    private final UserContextService userContextService;

    @GetMapping("/experiment-results")
    public String init(Model model, @PageableDefault(size = 5) Pageable pageable,
                       @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
                       @ModelAttribute("errorMessage") final ArrayList<String> errorMessage) {
        ExperimentResultData.ExperimentResultSimpleList experimentResultExperiments = experimentResultService.getExperimentResultExperiments(pageable);
        ControllerUtils.addMessagesToModel(model, experimentResultExperiments);
        model.addAttribute("result", experimentResultExperiments);
        return "dispatcher/ai/experiment-result/experiment-results";
    }

    // for AJAX
    @PostMapping("/experiment-results-part")
    public String getExperiments(Model model, @PageableDefault(size = 5) Pageable pageable) {
        ExperimentResultData.ExperimentResultSimpleList experimentResults = experimentResultService.getExperimentResultExperiments(pageable);
        model.addAttribute("result", experimentResults);
        return "dispatcher/ai/experiment-result/experiment-results :: table";
    }

    @GetMapping(value = "/experiment-result-info/{id}")
    public String info(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, @ModelAttribute("errorMessage") final String errorMessage) {
        ExperimentResultData.ExperimentInfoExtended result = experimentResultTopLevelService.getExperimentInfoExtended(id);
        if (result.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.getErrorMessagesAsList());
            return "redirect:/dispatcher/ai/experiment-result/experiment-results";
        }

        if (result.isInfoMessages()) {
            model.addAttribute("infoMessages", result.infoMessages);
        }

        model.addAttribute("experimentResult", result.experimentResult);
        model.addAttribute("experiment", result.experiment);
        model.addAttribute("experimentInfo", result.experimentInfo);
        return "dispatcher/ai/experiment-result/experiment-result-info";
    }

    @GetMapping("/experiment-result-delete/{id}")
    public String delete(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        ExperimentResultData.ExperimentResultSimpleResult result = experimentResultTopLevelService.getExperimentResultData(id);
        if (result.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.getErrorMessagesAsList());
            return "redirect:/dispatcher/ai/experiment-result/experiment-results";
        }

        if (result.isInfoMessages()) {
            model.addAttribute("infoMessages", result.infoMessages);
        }

        model.addAttribute("experiment", result.experimentResult);
        model.addAttribute("experimentResultId", id);
        return "dispatcher/ai/experiment-result/experiment-result-delete";
    }

    @PostMapping("/experiment-result-delete-commit")
    public String deleteCommit(Long experimentResultId, final RedirectAttributes redirectAttributes) {
        OperationStatusRest status = experimentResultTopLevelService.experimentResultDeleteCommit(experimentResultId);
        ControllerUtils.initRedirectAttributes(redirectAttributes, status);
        return REDIRECT_DISPATCHER_EXPERIMENT_RESULT_EXPERIMENT_RESULTS;
    }

    @GetMapping(value= "/experiment-result-export/{experimentResultId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<AbstractResource> downloadProcessingResult(
            HttpServletRequest request,
            @PathVariable("experimentResultId") Long experimentResultId) {
        CleanerInfo resource = experimentResultTopLevelService.exportExperimentResultToFile(experimentResultId);;
        if (resource==null) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        }
        request.setAttribute(Consts.RESOURCES_TO_CLEAN, resource.toClean);
        return resource.entity == null ? new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE) : resource.entity;
    }

    @PostMapping(value = "/experiment-result-upload-from-file")
    public String uploadExperimentResult(final MultipartFile file, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = experimentResultTopLevelService.uploadExperiment(file, context);
        ControllerUtils.initRedirectAttributes(redirectAttributes, operationStatusRest);
        return REDIRECT_DISPATCHER_EXPERIMENT_RESULT_EXPERIMENT_RESULTS;
    }

    @GetMapping(value= "/experiment-result-import")
    public String importExperiment(Model model) {
        return "dispatcher/ai/experiment-result/experiment-result-import";
    }

    @GetMapping(value = "/experiment-result-feature-progress/{experimentResultId}/{experimentId}/{featureId}")
    public String getFeatures(
            Model model,
            @PathVariable Long experimentResultId,
            @PathVariable Long experimentId,
            @PathVariable Long featureId,
            final RedirectAttributes redirectAttributes) {

        ExperimentResultData.ExperimentFeatureExtendedResult experimentProgressResult =
                experimentResultTopLevelService.getExperimentFeatureExtended(experimentResultId, experimentId, featureId);
        if (experimentProgressResult.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", experimentProgressResult.getErrorMessagesAsList());
            return "redirect:/dispatcher/ai/experiment-result/experiment-result-info/" + experimentResultId;
        }
        model.addAttribute("metrics", experimentProgressResult.metricsResult);
        model.addAttribute("params", experimentProgressResult.hyperParamResult);
        model.addAttribute("tasks", experimentProgressResult.tasks);
        model.addAttribute("feature", experimentProgressResult.experimentFeature);
        model.addAttribute("consoleResult", experimentProgressResult.consoleResult);
        model.addAttribute("experimentId", experimentId);
        model.addAttribute("experimentResultId", experimentResultId);

        return "dispatcher/ai/experiment-result/experiment-result-feature-progress";
    }

    @PostMapping("/experiment-result-feature-plot-data-part/{experimentResultId}/{experimentId}/{featureId}/{params}/{paramsAxis}/part")
    @ResponseBody
    public ExperimentResultData.PlotData getPlotData(
            @PathVariable Long experimentResultId,
            @PathVariable Long experimentId, @PathVariable Long featureId,
            @PathVariable String[] params, @PathVariable String[] paramsAxis) {
        return experimentResultTopLevelService.getPlotData(experimentResultId, featureId, params, paramsAxis);
    }

    @PostMapping("/experiment-result-feature-progress-console-part/{experimentResultId}/{taskId}")
    public String getTasksConsolePart(
            Model model,
            @PathVariable(name = "experimentResultId") Long experimentResultId,
            @PathVariable(name = "taskId") Long taskId
    ) {
        ExperimentResultData.ConsoleResult result = experimentResultTopLevelService.getTasksConsolePart(experimentResultId, taskId);
        model.addAttribute("consoleResult", result);
        return "dispatcher/ai/experiment-result/experiment-result-feature-progress :: fragment-console-table";
    }

    @PostMapping("/experiment-result-feature-progress-part/{experimentResultId}/{experimentId}/{featureId}/{params}/part")
    public String getFeatureProgressPart(Model model, @PathVariable Long experimentResultId, @PathVariable Long experimentId, @PathVariable Long featureId, @PathVariable String[] params, @SuppressWarnings("DefaultAnnotationParam") @PageableDefault(size = 10) Pageable pageable) {
        ExperimentResultData.ExperimentFeatureExtendedResult experimentProgressResult =
                experimentResultTopLevelService.getFeatureProgressPart(experimentResultId, featureId, params, pageable);

        model.addAttribute("metrics", experimentProgressResult.metricsResult);
        model.addAttribute("params", experimentProgressResult.hyperParamResult);
        model.addAttribute("tasks", experimentProgressResult.tasks);
        model.addAttribute("feature", experimentProgressResult.experimentFeature);
        model.addAttribute("consoleResult", experimentProgressResult.consoleResult);
        model.addAttribute("experimentId", experimentId);
        model.addAttribute("experimentResultId", experimentResultId);

        return "dispatcher/ai/experiment-result/experiment-result-feature-progress :: fragment-table";
    }


}
