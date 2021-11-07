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

package ai.metaheuristic.ai.dispatcher.test.tx;

import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Serge
 * Date: 9/29/2020
 * Time: 2:36 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TxTestingTopLevelService {

    private final TxTestingService txTestingService;

    @Transactional(propagation = Propagation.NEVER)
    public String updateWithSyncSingle(Long execContextId, Long taskId) {
        return ExecContextSyncService.getWithSync(execContextId, () -> txTestingService.updateSingle(execContextId, taskId));
    }

    @Transactional(propagation = Propagation.NEVER)
    public String updateWithSyncDouble(Long execContextId, Long taskId) {
        return ExecContextSyncService.getWithSync(execContextId, () -> txTestingService.updateDouble(execContextId, taskId));
    }
}
