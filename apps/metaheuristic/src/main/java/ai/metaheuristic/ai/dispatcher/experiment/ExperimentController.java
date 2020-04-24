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

package ai.metaheuristic.ai.dispatcher.experiment;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.Experiment;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeController;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeTopLevelService;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.api.dispatcher.ExecContext;
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
@RequestMapping("/dispatcher/ai/experiment")
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
public class ExperimentController {

    private static final String REDIRECT_DISPATCHER_EXPERIMENTS = "redirect:/dispatcher/ai/experiment/experiments";
    private final ExperimentTopLevelService experimentTopLevelService;
    private final ExecContextService execContextService;
    private final ExecContextCache execContextCache;
    private final ExperimentCache experimentCache;
    private final SourceCodeTopLevelService sourceCodeTopLevelService;
    private final UserContextService userContextService;

    @GetMapping("/experiments")
    public String getExperiments(Model model, @PageableDefault(size = 5) Pageable pageable,
                       @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
                       @ModelAttribute("errorMessage") final ArrayList<String> errorMessage) {
        ExperimentApiData.ExperimentsResult experiments = experimentTopLevelService.getExperiments(pageable);
        ControllerUtils.addMessagesToModel(model, experiments);
        model.addAttribute("result", experiments);
        return "dispatcher/ai/experiment/experiments";
    }

    // for AJAX
    @PostMapping("/experiments-part")
    public String getExperimentsAjax(Model model, @PageableDefault(size = 5) Pageable pageable) {
        ExperimentApiData.ExperimentsResult experiments = experimentTopLevelService.getExperiments(pageable);
        model.addAttribute("result", experiments);
        return "dispatcher/ai/experiment/experiments :: table";
    }

    @GetMapping(value = "/experiment-add")
    public String add(@ModelAttribute("experiment") ExperimentApiData.ExperimentData experiment) {
        return "dispatcher/ai/experiment/experiment-add-form";
    }

    @GetMapping(value = "/experiment-edit/{id}")
    public String edit(@PathVariable Long id, Model model, @ModelAttribute("errorMessage") final String errorMessage, final RedirectAttributes redirectAttributes) {
        ExperimentApiData.ExperimentsEditResult r = experimentTopLevelService.editExperiment(id);
        if (r.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", r.getErrorMessagesAsList());
            return REDIRECT_DISPATCHER_EXPERIMENTS;
        }

        model.addAttribute("simpleExperiment", r.simpleExperiment);
        return "dispatcher/ai/experiment/experiment-edit-form";
    }

    @GetMapping("/exec-context-target-state/{experimentId}/{state}/{id}")
    public String execContextTargetExecState(@PathVariable Long experimentId, @PathVariable String state,
                                          @PathVariable Long id, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = sourceCodeTopLevelService.changeExecContextState(state, id, context);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.getErrorMessagesAsList());
            return SourceCodeController.REDIRECT_DISPATCHER_SOURCE_CODES;
        }
        return "redirect:/dispatcher/ai/experiment/experiment-info/" + experimentId;
    }


    @PostMapping("/experiment-add-form-commit")
    public String addFormCommit(ExperimentApiData.ExperimentData experiment, final RedirectAttributes redirectAttributes) {
        OperationStatusRest status = experimentTopLevelService.addExperimentCommit(experiment);
        if (status.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", status.getErrorMessagesAsList());
            return "dispatcher/experiment/experiment-add-form";
        }
        return REDIRECT_DISPATCHER_EXPERIMENTS;
    }

    @PostMapping("/experiment-edit-form-commit")
    public String editFormCommit(ExperimentApiData.SimpleExperiment simpleExperiment, final RedirectAttributes redirectAttributes) {
        OperationStatusRest status = experimentTopLevelService.editExperimentCommit(simpleExperiment);
        if (status.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", status.getErrorMessagesAsList());
        }
        return "redirect:/dispatcher/ai/experiment/experiment-edit/" + simpleExperiment.getId();
    }

    @GetMapping("/experiment-delete/{id}")
    public String delete(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        ExperimentApiData.ExperimentResult result = experimentTopLevelService.getExperimentWithoutProcessing(id);
        if (result.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.getErrorMessagesAsList());
            return REDIRECT_DISPATCHER_EXPERIMENTS;
        }

        model.addAttribute("experiment", result.experiment);
        return "dispatcher/ai/experiment/experiment-delete";
    }

    @PostMapping("/experiment-delete-commit")
    public String deleteCommit(Long id, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "#285.260 experiment wasn't found, experimentId: " + id);
            return REDIRECT_DISPATCHER_EXPERIMENTS;
        }
        if (experiment.execContextId !=null) {
            ExecContext wb = execContextCache.findById(experiment.execContextId);
            if (wb != null) {
                OperationStatusRest operationStatusRest = sourceCodeTopLevelService.deleteExecContextById(experiment.execContextId, context);
                if (operationStatusRest.isErrorMessages()) {
                    redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.getErrorMessagesAsList());
                    return REDIRECT_DISPATCHER_EXPERIMENTS;
                }
            }
        }
        OperationStatusRest status = experimentTopLevelService.experimentDeleteCommit(id);
        if (status.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", status.getErrorMessagesAsList());
        }
        return REDIRECT_DISPATCHER_EXPERIMENTS;
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
            redirectAttributes.addFlashAttribute("errorMessage", status.getErrorMessagesAsList());
        }
        return "redirect:/dispatcher/ai/experiment/experiment-info/"+experimentId;
    }
}
