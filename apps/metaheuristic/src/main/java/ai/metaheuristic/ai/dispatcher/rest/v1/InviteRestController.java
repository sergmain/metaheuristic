/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.rest.v1;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.dispatcher.invite.InviteData;
import ai.metaheuristic.ai.dispatcher.invite.InviteTokenUtils;
import ai.metaheuristic.ai.dispatcher.invite.InviteTxService;
import ai.metaheuristic.ai.sec.RoleService;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.account.UserContext;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Administration of invite tokens: create and withdraw.
 *
 * <p>Every operation is scoped to the CALLER'S OWN company, taken from the
 * authenticated principal and never from a request parameter. An admin of one
 * company cannot mint an account into another company's tenancy, and there is
 * no parameter through which they could try.
 *
 * <p>The roles requested for the minted account are validated against the
 * installed role set BEFORE an invite is issued. Without that, an admin could
 * name an arbitrary string and discover later that the account it minted holds
 * an authority nobody meant to grant — and by then the invite would already be
 * in an outside party's hands, which is far too late to take back.
 *
 * <p>Redemption is deliberately NOT here: it lives on the anonymous surface,
 * because the redeemer has no credential at that point by definition.
 *
 * @author Sergio Lissner
 * Date: 8/2/2026
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/invite")
@Slf4j
@Profile("dispatcher")
@CrossOrigin
@PreAuthorize("hasAnyRole('ADMIN')")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class InviteRestController {

    private final InviteTxService inviteTxService;
    private final UserContextService userContextService;
    private final RoleService roleService;

    @Data
    @NoArgsConstructor
    public static class NewInviteRequest {
        /** Roles the minted account will carry. Validated against the installed role set. */
        @Nullable
        public String roles;
        @Nullable
        public String description;
        /** Time-to-live in hours; falls back to the default when absent or non-positive. */
        @Nullable
        public Integer ttlHours;
    }

    @PostMapping("/invite-add-commit")
    public InviteData.CreatedInvite create(@RequestBody NewInviteRequest request, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);

        if (request.roles==null || request.roles.isBlank()) {
            return InviteData.CreatedInvite.error("01.241.020 roles must not be blank");
        }

        // Validate every requested role against the installed set. A role that
        // does not exist would otherwise be stored verbatim and granted verbatim.
        // Company-aware, following the AccountTxService precedent: company 1 has
        // a wider role set, and checking it against the regular list would refuse
        // roles its own admin is entitled to grant.
        final var possibleRoles = Consts.ID_1.equals(context.getCompanyId())
                ? roleService.getCompany1PossibleRoles()
                : roleService.getPossibleRoles();
        for (String role : request.roles.split(",")) {
            final String trimmed = role.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!possibleRoles.contains(trimmed)) {
                return InviteData.CreatedInvite.error("01.241.040 Unknown role: " + trimmed);
            }
        }

        final long ttlMillis = request.ttlHours==null || request.ttlHours <= 0
                ? InviteTokenUtils.DEFAULT_TTL_MILLIS
                : request.ttlHours * 3600_000L;

        return inviteTxService.createInvite(
                context.getCompanyId(), request.roles, context.getAccountId(),
                request.description, ttlMillis);
    }

    @PostMapping("/invite-withdraw-commit/{inviteId}")
    public OperationStatusRest withdraw(@PathVariable Long inviteId, Authentication authentication) {
        UserContext context = userContextService.getContext(authentication);
        return inviteTxService.withdraw(inviteId, context.getCompanyId());
    }
}
