/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.license;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.commons.spi.license.DeploymentValues;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Exposes the resolved deployment values as a bean, so the status page reports the SAME values the
 * verify path was checked against.
 *
 * <p>Trivial by design. It exists only so nobody re-derives the mapping at a second call site: two
 * copies of "which database are we on" would eventually disagree, and the disagreement would show
 * up as a status page insisting the deployment is licensed while the gates say it is not.
 *
 * @author Serge
 */
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class DeploymentValuesResolverHolder {

    private final Globals globals;

    public DeploymentValues current() {
        return DeploymentValuesResolver.resolve(globals.activeProfilesSet);
    }
}
