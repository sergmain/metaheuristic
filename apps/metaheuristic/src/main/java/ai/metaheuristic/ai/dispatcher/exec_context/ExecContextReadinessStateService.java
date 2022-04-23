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

package ai.metaheuristic.ai.dispatcher.exec_context;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Serge
 * Date: 9/27/2021
 * Time: 12:22 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextReadinessStateService {
    private final LinkedList<Long> notReadyExecContextIds = new LinkedList<>();

    public boolean addAll(List<Long> execContextIds) {
        synchronized (notReadyExecContextIds) {
            return notReadyExecContextIds.addAll(execContextIds);
        }
    }

    public boolean isNotReady(Long execContextId) {
        synchronized (notReadyExecContextIds) {
            return !notReadyExecContextIds.isEmpty() && notReadyExecContextIds.contains(execContextId);
        }
    }

    public boolean remove(Long execContextId) {
        synchronized (notReadyExecContextIds) {
            return notReadyExecContextIds.remove(execContextId);
        }
    }


}
