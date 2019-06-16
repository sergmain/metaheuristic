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

package ai.metaheuristic.ai.registry.address;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.launchpad.beans.LaunchpadAddress;
import ai.metaheuristic.ai.launchpad.repositories.LaunchpadAddressRepository;
import ai.metaheuristic.ai.utils.checksum.ChecksumWithSignatureService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/registry/address")
@Slf4j
@Profile("launchpad")
public class LaunchpadAddressController {

    @Data
    public static class Result {
        public Iterable<LaunchpadAddress> items;
    }

    private final Globals globals;
    private final LaunchpadAddressRepository launchpadAddressRepository;

    public LaunchpadAddressController(Globals globals, LaunchpadAddressRepository launchpadAddressRepository) {
        this.globals = globals;
        this.launchpadAddressRepository = launchpadAddressRepository;
    }

    @GetMapping("/addresses")
    public String addresses(@ModelAttribute Result result, @ModelAttribute("errorMessage") final String errorMessage) {
        result.items = launchpadAddressRepository.findAll();
        return "registry/address/addresses";
    }

    @GetMapping(value = "/address-add")
    public String add(@ModelAttribute("address") LaunchpadAddress address, @ModelAttribute("errorMessage") final String errorMessage) {
        return "registry/address/address-add";
    }

    @PostMapping("/address-add-commit")
    public String addFormCommit(Model model, LaunchpadAddress address) {
        return processLaunchpadAddressCommit(model, address,
                "registry/address/address-add",
                "redirect:/registry/address/addresses");
    }

    private String processLaunchpadAddressCommit(Model model, LaunchpadAddress address, String errorTarget, String normalTarget) {
        if (StringUtils.isBlank(address.url)) {
            model.addAttribute("errorMessage", "#660.20 url is empty");
            return errorTarget;
        }
        if (StringUtils.isBlank(address.description)) {
            model.addAttribute("errorMessage", "#660.30 description is empty");
            return errorTarget;
        }
        if (StringUtils.isBlank(address.signature)) {
            model.addAttribute("errorMessage", "#660.33 checksum is empty");
            return errorTarget;
        }

        boolean isSignatureOk = ChecksumWithSignatureService.isValid(address.url.getBytes(), address.signature, globals.launchpadPublicKey);
        if (!isSignatureOk) {
            model.addAttribute("errorMessage", "#660.43 signature isn't valid");
            return errorTarget;
        }

        launchpadAddressRepository.saveAndFlush(address);
        return normalTarget;
    }

    @GetMapping("/address-delete/{id}")
    public String delete(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        final LaunchpadAddress address = launchpadAddressRepository.findById(id).orElse(null);
        if (address == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#660.50 address wasn't found, id: "+id );
            return "redirect:/registry/address/addresses";
        }
        model.addAttribute("address", address);
        return "registry/address/address-delete";
    }

    @PostMapping("/address-delete-commit")
    public String deleteCommit(Long id, final RedirectAttributes redirectAttributes) {
        final LaunchpadAddress address = launchpadAddressRepository.findById(id).orElse(null);
        if (address == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#660.55 address wasn't found, addressId: " + id);
            return "redirect:/registry/address/addresses";
        }
        launchpadAddressRepository.deleteById(id);
        return "redirect:/registry/address/addresses";
    }

}
