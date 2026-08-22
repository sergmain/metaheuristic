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

package ai.metaheuristic.ai.dispatcher.comm_channel;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.ai.sec.RoleService;
import ai.metaheuristic.commons.account.CommChannelServiceProvider;
import ai.metaheuristic.commons.account.CommChannelServiceProvider.CommChannelService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The services an outside party may be issued a communication channel to,
 * aggregated from every {@link CommChannelServiceProvider} bean.
 *
 * <p><b>Validated at startup, and the application refuses to start on a bad
 * declaration.</b> Every check below guards a mistake that would otherwise
 * surface as a working channel with the wrong authority — which is the worst
 * possible time to find out, because by then a credential is in an outside
 * party's hands and nothing looks wrong from either side.
 *
 * <p>Error code prefix: {@code 01.239.} (unique to this class).
 *
 * @author Sergio Lissner
 * Date: 8/2/2026
 */
@Service
@Profile("dispatcher")
@Slf4j
public class CommChannelServiceRegistry {

    private final Map<String, CommChannelService> services;

    public CommChannelServiceRegistry(
            @Autowired(required = false) List<CommChannelServiceProvider> providers,
            RoleService roleService) {

        final Map<String, CommChannelService> map = new LinkedHashMap<>();
        final List<String> errors = new ArrayList<>();

        if (providers!=null) {
            for (CommChannelServiceProvider provider : providers) {
                final List<CommChannelService> declared = provider.getCommChannelServices();
                if (declared==null) {
                    continue;
                }
                for (CommChannelService svc : declared) {
                    if (svc.tag()==null || svc.tag().isBlank()) {
                        errors.add("01.239.020 blank service tag from " + provider.getClass().getSimpleName());
                        continue;
                    }
                    if (map.containsKey(svc.tag())) {
                        // Two services under one tag means an operator issuing a token
                        // cannot know which authority they are handing out.
                        errors.add("01.239.040 duplicate service tag: " + svc.tag());
                        continue;
                    }
                    if (svc.role()==null || !svc.role().startsWith("ROLE_")) {
                        errors.add("01.239.060 service '" + svc.tag()
                                + "' names a role that isn't ROLE_*: " + svc.role());
                        continue;
                    }
                    if (!roleService.isValidRole(svc.role()) && !roleService.isValidManagementCompanyRole(svc.role())) {
                        errors.add("01.239.080 service '" + svc.tag()
                                + "' names an unknown role: " + svc.role());
                        continue;
                    }
                    // ❗ The binding that makes the two mechanisms consistent. A service
                    // pointing at an admin-assignable role would mint accounts whose
                    // roles an admin could then edit by hand, so the channel would look
                    // owned and not be.
                    if (roleService.getRoleManager(svc.role())!=EnumsApi.RoleManager.commChannel) {
                        errors.add("01.239.100 service '" + svc.tag() + "' names role " + svc.role()
                                + " which is managed by '" + roleService.getRoleManager(svc.role())
                                + "', not 'commChannel'");
                        continue;
                    }
                    map.put(svc.tag(), svc);
                    log.info("Registered comm-channel service '{}' -> {}", svc.tag(), svc.role());
                }
            }
        }

        if (!errors.isEmpty()) {
            // Fail fast. A misdeclared service is a security misconfiguration, and
            // starting anyway would leave it to be discovered by whoever activates
            // a token against it.
            throw new IllegalStateException(
                    "01.239.120 comm-channel service registry is invalid:\n" + String.join("\n", errors));
        }

        this.services = Map.copyOf(map);
        log.info("Total comm-channel services: {}", this.services.keySet());
    }

    @Nullable
    public CommChannelService findByTag(String tag) {
        return services.get(tag);
    }

    public List<CommChannelService> getServices() {
        return List.copyOf(services.values());
    }

    public boolean isKnownTag(String tag) {
        return services.containsKey(tag);
    }
}
