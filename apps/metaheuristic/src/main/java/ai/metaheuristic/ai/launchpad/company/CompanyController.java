/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.company;

import ai.metaheuristic.ai.launchpad.beans.Company;
import ai.metaheuristic.ai.launchpad.data.CompanyData;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;

/**
 * @author Serge
 * Date: 10/27/2019
 * Time: 8:48 PM
 */

@SuppressWarnings("Duplicates")
@Controller
@RequestMapping("/launchpad/company")
@Profile("launchpad")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MASTER_ADMIN')")
public class CompanyController {

    private final CompanyTopLevelService companyTopLevelService;

    @GetMapping("/companies")
    public String companies(Model model,
                           @PageableDefault(size = CompanyTopLevelService.ROWS_IN_TABLE) Pageable pageable,
                           @ModelAttribute("infoMessages") final ArrayList<String> infoMessages,
                           @ModelAttribute("errorMessage") final ArrayList<String> errorMessage) {

        CompanyData.CompaniesResult companies = companyTopLevelService.getCompanies(pageable);
        ControllerUtils.addMessagesToModel(model, companies);
        model.addAttribute("result", companies);
        return "launchpad/company/companies";
    }

    // for AJAX
    @PostMapping("/companies-part")
    public String getCompaniesViaAJAX(Model model, @PageableDefault(size=CompanyTopLevelService.ROWS_IN_TABLE) Pageable pageable )  {
        CompanyData.CompaniesResult companies = companyTopLevelService.getCompanies(pageable);
        model.addAttribute("result", companies);
        return "launchpad/company/companies :: table";
    }

    @GetMapping(value = "/company-add")
    public String add(@ModelAttribute("company") Company company) {
        return "launchpad/company/company-add";
    }

    @PostMapping("/company-add-commit")
    public String addFormCommit(Model model, Company company) {
        OperationStatusRest operationStatusRest = companyTopLevelService.addCompany(company);
        if (operationStatusRest.isErrorMessages()) {
            model.addAttribute("errorMessage", operationStatusRest.errorMessages);
            return "launchpad/company/company-add";
        }
        return "redirect:/launchpad/company/companies";
    }

    @GetMapping(value = "/company-edit/{id}")
    public String edit(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes){
        CompanyData.CompanyResult companyResult = companyTopLevelService.getCompany(id);
        if (companyResult.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", companyResult.errorMessages);
            return "redirect:/launchpad/company/companies";
        }
        model.addAttribute("company", companyResult.company);
        return "launchpad/company/company-edit";
    }

    @PostMapping("/company-edit-commit")
    public String editFormCommit(Long id, String name, final RedirectAttributes redirectAttributes) {
        OperationStatusRest operationStatusRest = companyTopLevelService.editFormCommit(id, name);
        if (operationStatusRest.isErrorMessages()) {
            redirectAttributes.addFlashAttribute("errorMessage", operationStatusRest.errorMessages);
        }
        if (operationStatusRest.isInfoMessages()) {
            redirectAttributes.addFlashAttribute("infoMessages", operationStatusRest.infoMessages);
        }
        return "redirect:/launchpad/company/companies";
    }
}


