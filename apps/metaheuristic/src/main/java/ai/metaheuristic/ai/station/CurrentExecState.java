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
package ai.metaheuristic.ai.station;

import ai.metaheuristic.ai.comm.Protocol;
import ai.metaheuristic.api.EnumsApi;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Profile("station")
public class CurrentExecState {

    // this is a map for holding the current status of Workbook, Not a task
    private final Map<String, Map<Long, EnumsApi.WorkbookExecState>> workbookState = new HashMap<>();

    private Map<String, AtomicBoolean> isInit = new HashMap<>();

    public boolean isInited(String launchpadUrl) {
        synchronized(workbookState) {
            return isInit.computeIfAbsent(launchpadUrl, v -> new AtomicBoolean(false)).get();
        }
    }

    void register(String launchpadUrl, List<Protocol.WorkbookStatus.SimpleStatus> statuses) {
        synchronized(workbookState) {
            isInit.computeIfAbsent(launchpadUrl, v -> new AtomicBoolean()).set(true);
            // statuses==null when there isn't any workbook
            if (statuses==null) {
                workbookState.computeIfAbsent(launchpadUrl, m -> new HashMap<>()).clear();
                return;
            }
            statuses.forEach(status -> workbookState.computeIfAbsent(launchpadUrl, m -> new HashMap<>()).put(status.workbookId, status.state));
            workbookState.forEach((k, v) -> {
                if (!k.equals(launchpadUrl)) {
                    return;
                }
                List<Long> ids = new ArrayList<>();
                v.forEach((key, value) -> {
                    boolean isFound = statuses.stream().anyMatch(status -> status.workbookId == key);
                    if (!isFound) {
                        ids.add(key);
                    }
                });
                ids.forEach(v::remove);
            });
        }
    }

    EnumsApi.WorkbookExecState getState(String host, long workbookId) {
        synchronized(workbookState) {
            if (!isInited(host)) {
                return EnumsApi.WorkbookExecState.UNKNOWN;
            }
            return workbookState.getOrDefault(host, Collections.emptyMap()).getOrDefault(workbookId, EnumsApi.WorkbookExecState.DOESNT_EXIST);
        }
    }

    boolean isState(String launchpadUrl, long workbookId, EnumsApi.WorkbookExecState state) {
        EnumsApi.WorkbookExecState currState = getState(launchpadUrl, workbookId);
        return currState!=null && currState==state;
    }

    boolean isStarted(String launchpadUrl, long workbookId) {
        return isState(launchpadUrl, workbookId, EnumsApi.WorkbookExecState.STARTED);
    }
}
