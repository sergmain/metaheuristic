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

package ai.metaheuristic.ai.mhbp.rest;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.context.UserContextService;
import ai.metaheuristic.ai.mhbp.chat.ChatService;
import ai.metaheuristic.ai.mhbp.chat.ChatTxService;
import ai.metaheuristic.ai.mhbp.data.ChatData;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 6/21/2023
 * Time: 11:42 PM
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/chat")
@Slf4j
@RequiredArgsConstructor
@Profile("dispatcher")
public class ChatRestController {

    private final ChatService chatService;
    private final ChatTxService chatTxService;
    private final UserContextService userContextService;

    @GetMapping("/chats")
    public ChatData.Chats chats(Pageable pageable, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        final List<ChatData.SimpleChat> chats = chatService.getChats(pageable, context);
        return new ChatData.Chats(chats);
    }

    @GetMapping("/chat/{chatId}")
    public ChatData.FullChat chats(@PathVariable Long chatId, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        final ChatData.FullChat chat = chatService.getChat(chatId, context);
        return chat;
    }

    @PostMapping("/ask-prompt/{chatId}")
    public OperationStatusRest addFormCommit(
            @PathVariable Long chatId,
            @RequestParam(name = "prompt") String prompt,
            Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return chatService.askPrompt(chatId, prompt, context);
    }

    @PostMapping("/chat-delete-commit")
    public OperationStatusRest deleteCommit(Long chatId, Authentication authentication) {
        DispatcherContext context = userContextService.getContext(authentication);
        return chatTxService.deleteChatById(chatId, context);
    }

}
