/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.series;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.data.ExperimentResultData;
import ai.metaheuristic.ai.dispatcher.data.SeriesData;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.data.OperationStatusRest;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 3/30/2021
 * Time: 2:50 PM
 */
@Controller
@RequestMapping("dispatcher/ai/series")
@Slf4j
@Profile("dispatcher")
@PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
@RequiredArgsConstructor
public class SeriesController {

    private static final String REDIRECT_DISPATCHER_SERIES = "redirect:/dispatcher/ai/series/series";

    private final SeriesTopLevelService seriesTopLevelService;
    private final SeriesService seriesService;
    private final UserContextService userContextService;

    @GetMapping("/series")
    public String getExperiments(Model model, @PageableDefault(size = 5) Pageable pageable,
                                 @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
                                 @ModelAttribute("errorMessage") final ArrayList<String> errorMessage) {
        SeriesData.SeriesesResult serieses = seriesTopLevelService.getSerieses(pageable);
        ControllerUtils.addMessagesToModel(model, serieses);
        model.addAttribute("result", serieses);
        return "dispatcher/ai/series/series";
    }

    // for AJAX
    @PostMapping("/series-part")
    public String getSeriesAjax(Model model, @PageableDefault(size = 5) Pageable pageable) {
        SeriesData.SeriesesResult serieses = seriesTopLevelService.getSerieses(pageable);
        model.addAttribute("result", serieses);
        return "dispatcher/ai/series/series :: table";
    }

    @GetMapping("/series-details/{id}")
    public String getExperiments(@PathVariable Long id, Model model,
                                 @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
                                 @ModelAttribute("errorMessage") final ArrayList<String> errorMessage) {
        SeriesData.SeriesDetails details = seriesTopLevelService.getSeriesDetails(id);
        ControllerUtils.addMessagesToModel(model, details);
        model.addAttribute("result", details);
        return "dispatcher/ai/series/series-details";
    }

    @GetMapping(value = "/series-add")
    public String add(Model model, @ModelAttribute("errorMessage") final String errorMessage, Authentication authentication, final RedirectAttributes redirectAttributes) {
        DispatcherContext context = userContextService.getContext(authentication);
        List<ExperimentResultData.SimpleExperimentResult> results = seriesTopLevelService.getExperimentResults();
        model.addAttribute("result", results);
        return "dispatcher/ai/series/series-add";
    }

    @PostMapping("/series-add-commit")
    public String addFormCommit(String name, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        OperationStatusRest status = seriesTopLevelService.addSeriesCommit(name, context);
        if (status.isErrorMessages()) {
            ControllerUtils.initRedirectAttributes(redirectAttributes, status);
            return "redirect:/dispatcher/ai/series/series-add";
        }
        return REDIRECT_DISPATCHER_SERIES;
    }

    @GetMapping(value = "/series-edit/{id}")
    public String edit(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, Authentication authentication){
        DispatcherContext context = userContextService.getContext(authentication);
        SeriesData.SeriesResult seriesResult = seriesTopLevelService.getSeries(id, context);
        if (seriesResult.isErrorMessages()) {
            ControllerUtils.initRedirectAttributes(redirectAttributes, seriesResult);
            return REDIRECT_DISPATCHER_SERIES;
        }
        model.addAttribute("result", seriesResult);
        return "dispatcher/ai/series/series-edit";
    }

    @PostMapping("/series-edit-commit")
    public String editCommit(Long id, String name,
                                 final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        OperationStatusRest seriesResult = seriesService.editCommit(id, name, context);
        ControllerUtils.initRedirectAttributes(redirectAttributes, seriesResult);
        return REDIRECT_DISPATCHER_SERIES;
    }
    @GetMapping("/series-delete/{id}")
    public String delete(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        SeriesData.SeriesResult seriesResult = seriesTopLevelService.getSeries(id, context);
        if (seriesResult.isErrorMessages()) {
            ControllerUtils.initRedirectAttributes(redirectAttributes, seriesResult);
            return REDIRECT_DISPATCHER_SERIES;
        }
        model.addAttribute("result", seriesResult);
        return "dispatcher/ai/series/series-delete";
    }

    @PostMapping("/series-delete-commit")
    public String deleteCommit(Long seriesId, final RedirectAttributes redirectAttributes, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        OperationStatusRest operationStatusRest = seriesService.deleteSeriesById(seriesId, context);
        ControllerUtils.initRedirectAttributes(redirectAttributes, operationStatusRest);
        return REDIRECT_DISPATCHER_SERIES;
    }
}
