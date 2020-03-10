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

package ai.metaheuristic.ai.dispatcher.processor;

import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.utils.ControllerUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * User: Serg
 * Date: 12.06.2017
 * Time: 20:21
 */
@Controller
@RequestMapping("/dispatcher/processor")
@Profile("dispatcher")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN')")
public class ProcessorController {

    private final ProcessorTopLevelService processorTopLevelService;

    @GetMapping("/processors")
    public String getProcessors(Model model, @PageableDefault(size = 5) Pageable pageable) {
        ProcessorData.ProcessorsResult processorsResultRest = processorTopLevelService.getProcessors(pageable);
        ControllerUtils.addMessagesToModel(model, processorsResultRest);
        model.addAttribute("result", processorsResultRest);
        return "dispatcher/processor/processors";
    }

    // for AJAX
    @PostMapping("/processors-part")
    public String getProcessorsForAjax(Model model, @PageableDefault(size = 5) Pageable pageable) {
        ProcessorData.ProcessorsResult processorsResultRest = processorTopLevelService.getProcessors(pageable);
        ControllerUtils.addMessagesToModel(model, processorsResultRest);
        model.addAttribute("result", processorsResultRest);
        return "dispatcher/processor/processors :: table";
    }

    @GetMapping(value = "/processor-edit/{id}")
    public String edit(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        ProcessorData.ProcessorResult processorResultRest = processorTopLevelService.getProcessor(id);
        if (processorResultRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", processorResultRest.getErrorMessagesAsList());
            return "redirect:/dispatcher/processor/processors";
        }
        ControllerUtils.addMessagesToModel(model, processorResultRest);
        model.addAttribute("processor", processorResultRest.processor);
        return "dispatcher/processor/processor-form";
    }

    @PostMapping("/processor-form-commit")
    public String saveProcessor(Processor processor, final RedirectAttributes redirectAttributes) {
        ProcessorData.ProcessorResult r = processorTopLevelService.saveProcessor(processor);
        if (r.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", r.getErrorMessagesAsList());
        }
        return "redirect:/dispatcher/processor/processors";
    }

    @GetMapping("/processor-delete/{id}")
    public String delete(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        ProcessorData.ProcessorResult processorResultRest = processorTopLevelService.getProcessor(id);
        if (processorResultRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", processorResultRest.getErrorMessagesAsList());
            return "redirect:/dispatcher/processor/processors";
        }
        model.addAttribute("processor", processorResultRest.processor);
        return "dispatcher/processor/processor-delete";
    }

    @PostMapping("/processor-delete-commit")
    public String deleteCommit(Long id, final RedirectAttributes redirectAttributes) {
        OperationStatusRest r = processorTopLevelService.deleteProcessorById(id);
        if (r.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", r.getErrorMessagesAsList());
        }
        return "redirect:/dispatcher/processor/processors";
    }

}
