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

package ai.metaheuristic.ai.dispatcher.southbridge;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.sec.GoogleAuthenticator;
import ai.metaheuristic.ai.sec.SecConsts;
import ai.metaheuristic.commons.S;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * User: Serg
 * Date: 26.01.14
 * Time: 22:13
 */
@RequestMapping("/2fa")
@Controller
@RequiredArgsConstructor
@Profile("dispatcher")
public class TwoFactorAuthController {

    private static final String ISSUER = "Metaheuristic";

    private final GoogleAuthenticator googleAuthenticator;
    private final UserContextService userContextService;

    @Data
    public static class TwoFactorAuthForm {
        @NotNull
        @Size(min = 6, max = 10)
        private String code;
    }

    @RequestMapping
    @PreAuthorize("hasAnyRole('MASTER_ADMIN')")
    public String createForm(Model uiModel, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        @NonNull Account a = context.getAccount();
        String secret = GoogleAuthenticator.generateSecretKey();
        String url = GoogleAuthenticator.getQRBarcodeURL(ISSUER+"-"+context.getUsername(), "localhost", secret, ISSUER);

        if (a.accountRoles.hasRole(SecConsts.ROLE_MASTER_ADMIN) && (S.b(a.secretKey) || !a.twoFA)) {
            // TODO 2019-10-30 set up secret key
/*
            UserServerBean user = entityDAO.setUserSercretCodeForCurrentUser(secret, a.id);
            if (user.isTwoStepAuth()) {
                return "2fa/already";
            }
*/

        }
        uiModel.addAttribute("urlForQrCode", url);
        populateEditForm(uiModel, new TwoFactorAuthForm());
        return "2fa/form";
    }

    @RequestMapping(method = RequestMethod.POST)
    public String submit(@Valid @ModelAttribute("form") TwoFactorAuthController.TwoFactorAuthForm form, BindingResult bindingResult, Model uiModel) {

        validateMore(form, bindingResult);

        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
/*
        UserServerBean user = entityDAO.getUserAsServerBean(userDetails.getUsername());
        String url = GoogleAuthenticator.getQRBarcodeURL("PPS-"+user.getName(), "localhost", user.getSecretKey(), ISSUER);
        uiModel.addAttribute("urlForQrCode", url);

        if (!bindingResult.hasErrors()) {

            boolean isValid = googleAuthenticator.check_code(user.getSecretKey(), Long.parseLong(form.getCode()), System.currentTimeMillis());
            if (isValid) {
                entityDAO.setTwoStepAuth(true, user.getId());
                return "redirect:/2fa/thanks";
            }
            else {
                bindingResult.rejectValue("code", "label_2step_auth_code_is_wrong");
            }
        }
*/

        populateEditForm(uiModel, form);
        return "2fa/form";
    }

    @RequestMapping("/thanks")
    public String thanks() {
        return "2fa/thanks";
    }

    private void validateMore(TwoFactorAuthForm form, BindingResult bindingResult) {
        if (!StringUtils.isNumeric(form.getCode())) {
            bindingResult.rejectValue("code", "label_2step_auth_code_has_to_contain_digit");
        }
    }

    private void populateEditForm(Model uiModel, TwoFactorAuthForm form) {
        uiModel.addAttribute("form", form);
    }
}
