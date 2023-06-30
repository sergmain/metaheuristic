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

package ai.metaheuristic.ai.mhbp.chat;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.mhbp.data.ChatData;
import ai.metaheuristic.ai.mhbp.repositories.ChatRepository;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 6/21/2023
 * Time: 11:50 PM
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Profile("dispatcher")
public class ChatService {

    public final ChatRepository chatRepository;

    public ChatData.Chats getChats(Pageable pageable, DispatcherContext context) {

        try {
            List<ChatData.SimpleChat> chats = chatRepository.findAll(pageable, context.getAccountId());
            return new ChatData.Chats(chats);
        }
        catch (Throwable th) {
            log.error("Error:", th);
            return new ChatData.Chats("Error: " + th.getMessage());
        }
    }

    public ChatData.FullChat getChat(Long chatId, DispatcherContext context) {

        return null;
    }

    public OperationStatusRest askPrompt(Long chatId, String prompt, DispatcherContext context) {
        return null;
    }
}
