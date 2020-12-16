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

package ai.metaheuristic.ai.dispatcher.variable;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Serge
 * Date: 9/28/2020
 * Time: 11:00 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class VariableTopLevelService {

    private final VariableService variableService;
    private final VariableRepository variableRepository;
    private final ExecContextSyncService execContextSyncService;
    private final ExecContextCache execContextCache;

    public void deleteOrphanVariables(List<Long> orphanExecContextIds) {
        TxUtils.checkTxNotExists();
        for (Long execContextId : orphanExecContextIds) {
            if (execContextCache.findById(execContextId)!=null) {
                log.warn("execContextId #{} wasn't deleted, actually", execContextId);
                continue;
            }

            List<Long> ids;
            while (!(ids = variableRepository.findAllByExecContextId(Consts.PAGE_REQUEST_100_REC, execContextId)).isEmpty()) {
                List<List<Long>> pages = CollectionUtils.parseAsPages(ids, 10);
                for (List<Long> page : pages) {
                    execContextSyncService.getWithSyncNullable(execContextId, () -> {
                        log.info("Found orphan variables, execContextId: #{}, variables #{}", execContextId, page);
                        variableService.deleteOrphanVariables(page);
                        return null;
                    });
                }
            }
        }
    }

}
