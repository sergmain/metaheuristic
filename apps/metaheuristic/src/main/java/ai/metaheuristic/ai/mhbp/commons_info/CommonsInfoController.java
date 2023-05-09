/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.mhbp.commons_info;

import ai.metaheuristic.ai.mhbp.data.CommonInfoData;
import ai.metaheuristic.ai.utils.ControllerUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;

/**
 * @author Sergio Lissner
 * Date: 2/28/2023
 * Time: 1:47 PM
 */
@Controller
@RequestMapping("/mhbp/commons")
@Slf4j
@RequiredArgsConstructor
@Profile("dispatcher")
//@PreAuthorize("hasAnyRole('ADMIN', 'DATA')")
public class CommonsInfoController {

    private static final String REDIRECT_MHBP_COMMONS_INFO = "redirect:/mhbp/commons/info";

    private final CommonsInfoService commonsInfoService;

    @GetMapping("/info")
    public String getExperiments(Model model, @PageableDefault(size = 5) Pageable pageable,
                                 @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
                                 @ModelAttribute("errorMessage") final ArrayList<String> errorMessage) {
        CommonInfoData.Info info = commonsInfoService.getInfo(pageable);
        ControllerUtils.addMessagesToModel(model, info);
        model.addAttribute("result", info);
        return "mhbp/commons/info";
    }

    // for AJAX
    @PostMapping("/info-part")
    public String getExperimentsAjax(Model model, @PageableDefault(size = 5) Pageable pageable) {
        CommonInfoData.Info info = null;
        model.addAttribute("result", info);
        return "mhbp/commons/info :: table";
    }

}
