/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.mhbp.beans.Api;
import ai.metaheuristic.ai.mhbp.beans.Chat;
import ai.metaheuristic.ai.mhbp.data.ChatData;
import ai.metaheuristic.ai.mhbp.yaml.chat_log.ChatLogParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * @author Sergio Lissner
 * Date: 7/5/2023
 * Time: 2:40 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
public class ChatLogService {

    private final ChatLogTxService chatLogTxService;

    public ChatLogService(@Autowired ChatLogTxService chatLogTxService) {
        this.chatLogTxService = chatLogTxService;
    }

    public void saveToChatLog(@Nullable Long chatId, @Nullable Long scenarioId, Api api, ChatData.ChatPrompt prompt, DispatcherContext context) {
        ChatLogParams params = new ChatLogParams();
        params.api = new ChatLogParams.Api(api.id, api.code);
        params.prompt = new ChatLogParams.Prompt(prompt.prompt, prompt.result, prompt.raw, prompt.error);
        params.chatId = chatId;
        params.scenarioId = scenarioId;
        params.stateless = false;

        try {
            chatLogTxService.save(params, context.getCompanyId(), context.getAccountId());
        }
        catch (Throwable th) {
            log.error("Error", th);
            // we can skip an error of saving to ChatLog
        }
    }


}