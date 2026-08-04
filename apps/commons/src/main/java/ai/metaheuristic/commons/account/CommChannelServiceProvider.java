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

package ai.metaheuristic.commons.account;

import java.util.List;

/**
 * Interface for modules to declare the SERVICES an outside party may be given a
 * communication channel to. Implementations are registered as Spring beans and
 * discovered automatically, the same way {@link RoleProvider} contributes roles.
 *
 * <p>A service is a <b>tag plus a role</b>. The tag is what an operator picks
 * when issuing a token; the role is what the account minted on activation will
 * carry. Nothing else about the module is visible here — a tag is an opaque
 * label to this mechanism, exactly as a role name is.
 *
 * <p><b>Declared in configuration, not in the database</b>, and the asymmetry
 * with channels themselves is deliberate: services change when a deployment
 * changes, channels change while a deployment runs. Issuing and revoking a
 * channel must never need a redeploy; adding a service reasonably may.
 *
 * @author Sergio Lissner
 * Date: 8/2/2026
 */
public interface CommChannelServiceProvider {

    /**
     * @param tag         operator-facing service identifier, unique across the
     *                    installation
     * @param role        the {@code ROLE_*} an activated channel account receives.
     *                    Must be declared {@link RoleManager#commChannel}-managed
     *                    by some {@link RoleProvider}, or the registry refuses to
     *                    start
     * @param description shown when choosing a service to issue against
     */
    record CommChannelService(String tag, String role, String description) {}

    /**
     * @return services this module offers channels to, never null
     */
    List<CommChannelService> getCommChannelServices();
}
