/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.processor.actors;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.processor.oauth.OAuthTokenHolder;
import ai.metaheuristic.ai.processor.tasks.OAuthTokenTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
public class OAuthTokenRequestorService extends AbstractTaskQueue<OAuthTokenTask> implements QueueProcessor {

    private final Globals globals;
    private final OAuthTokenHolder oAuthTokenHolder;

    public void process() {
        if (globals.isUnitTesting) {
            return;
        }
        if (!globals.processor.enabled) {
            return;
        }

        OAuthTokenTask task;
        while((task = poll())!=null) {
            String token = null;
            
            // localhost:8080/oauth/token -d "grant_type=password&scope=read&username=greg&password=turnquist" -u foo:bar
            // ...


            if (token!=null) {
                oAuthTokenHolder.setToken(token);
            }
        }
        if (oAuthTokenHolder.getToken()==null) {
            add(new OAuthTokenTask());
        }
    }
}
