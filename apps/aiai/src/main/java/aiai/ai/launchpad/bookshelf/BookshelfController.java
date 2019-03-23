/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.bookshelf;

import aiai.ai.Enums;
import aiai.ai.launchpad.beans.Experiment;
import aiai.ai.launchpad.data.OperationStatusRest;
import aiai.ai.launchpad.experiment.ExperimentCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/launchpad/bookshelf")
@Slf4j
@Profile("launchpad")
public class BookshelfController {

    private final BookshelfService bookshelfService;
    private final ExperimentCache experimentCache;

    public BookshelfController(BookshelfService bookshelfService, ExperimentCache experimentCache) {
        this.bookshelfService = bookshelfService;
        this.experimentCache = experimentCache;
    }

    @GetMapping(value = "/experiment-to-bookshelf/{id}")
    public String toBookshelf(@PathVariable Long id, final RedirectAttributes redirectAttributes) {

        Experiment experiment = experimentCache.findById(id);
        if (experiment==null) {
            redirectAttributes.addFlashAttribute("errorMessages",
                    "# can't find experiment for id: " + id);
            return "redirect:/launchpad/experiment-info/"+id;
        }

        if (experiment.flowInstanceId==null) {
            redirectAttributes.addFlashAttribute("errorMessages",
                    "# This experiment isn't bound to FlowInstance");
            return "redirect:/launchpad/experiment-info/"+id;
        }

        OperationStatusRest status = bookshelfService.toBookshelf(experiment.flowInstanceId, id);
        if (status.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", status.errorMessages);
        }
        else {
            redirectAttributes.addFlashAttribute("infoMessages",
                    "Experiment was successfully stored to bookshelf");
        }
        return "redirect:/launchpad/experiment-info/"+id;

    }


}
