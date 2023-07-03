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
import ai.metaheuristic.ai.mhbp.api.ApiService;
import ai.metaheuristic.ai.mhbp.beans.Api;
import ai.metaheuristic.ai.mhbp.beans.Chat;
import ai.metaheuristic.ai.mhbp.data.ApiData;
import ai.metaheuristic.ai.mhbp.data.ChatData;
import ai.metaheuristic.ai.mhbp.repositories.ApiRepository;
import ai.metaheuristic.ai.mhbp.repositories.ChatRepository;
import ai.metaheuristic.ai.mhbp.yaml.chat.ChatParams;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
@Profile("dispatcher")
public class ChatService {

    private final ChatRepository chatRepository;
    private final ChatTxService chatTxService;
    private final ApiService apiService;
    private final ApiRepository apiRepository;

    public ChatService(@Autowired ChatRepository chatRepository,
                       @Autowired ChatTxService chatTxService,
                       @Autowired ApiService apiService,
                       @Autowired ApiRepository apiRepository) {
        this.chatRepository = chatRepository;
        this.chatTxService = chatTxService;
        this.apiService = apiService;
        this.apiRepository = apiRepository;
    }

    public ChatData.Chats getChats(Pageable pageable, DispatcherContext context) {
        try {
            List<ChatData.SimpleChat> chats = chatRepository.findIds(pageable, context.getAccountId()).stream()
                    .map(this::to).toList();
            return new ChatData.Chats(chats);
        }
        catch (Throwable th) {
            log.error("Error:", th);
            return new ChatData.Chats("Error: " + th.getMessage());
        }
    }

    private ChatData.SimpleChat to(Long id) {
        Chat chat = chatRepository.findById(id).orElseThrow();
        ChatParams params = chat.getChatParams();
        return new ChatData.SimpleChat(chat.id, chat.name, chat.createdOn, new ApiData.ApiUid(params.api.apiId, params.api.code));
    }

    public ChatData.FullChat getChat(Long chatId, DispatcherContext context) {

        return null;
    }

    public OperationStatusRest askPrompt(Long chatId, String prompt, DispatcherContext context) {
        return null;
    }


    public ChatData.ApiForCompany getApiForCompany(DispatcherContext context) {
        ChatData.ApiForCompany r = new ChatData.ApiForCompany();

        r.apis = apiService.getApisAllowedForCompany(context).stream()
                .map(o->new ApiData.ApiUid(o.id, o.code))
                .toList();
        return r;
    }

    public OperationStatusRest createChat(String name, String apiId, long companyId, long accountId, DispatcherContext context) {
        try {
            Api api = apiRepository.findById(Long.parseLong(apiId)).orElse(null);
            if (api==null || api.companyId!=context.getCompanyId()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "229.480 apiId is null");
            }
            ChatParams.Api apiRef = new ChatParams.Api(api.id, api.code);

            return chatTxService.createChat(name, apiRef, companyId, accountId);
        }
        catch (Throwable th) {
            log.error("Error", th);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, th.getMessage());
        }
    }

}
