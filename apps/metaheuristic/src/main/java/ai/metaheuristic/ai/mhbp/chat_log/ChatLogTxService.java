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

package ai.metaheuristic.ai.mhbp.chat_log;

import ai.metaheuristic.ai.mhbp.beans.ChatLog;
import ai.metaheuristic.ai.mhbp.repositories.ChatLogRepository;
import ai.metaheuristic.ai.mhbp.yaml.chat_log.ChatLogParams;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Sergio Lissner
 * Date: 7/5/2023
 * Time: 2:12 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
public class ChatLogTxService {

    private final ChatLogRepository chatLogRepository;

    public ChatLogTxService(@Autowired ChatLogRepository chatLogRepository) {
        this.chatLogRepository = chatLogRepository;
    }

    @Transactional
    public OperationStatusRest save(ChatLogParams params, long companyId, long accountId) {
        ChatLog chatLog = new ChatLog();
        chatLog.companyId = companyId;
        chatLog.accountId = accountId;
        chatLog.createdOn = System.currentTimeMillis();
        chatLog.updateParams(params);
        chatLogRepository.save(chatLog);

        return OperationStatusRest.OPERATION_STATUS_OK;
    }
}
