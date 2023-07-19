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
import ai.metaheuristic.ai.mhbp.chat_log.ChatLogService;
import ai.metaheuristic.ai.mhbp.data.ApiData;
import ai.metaheuristic.ai.mhbp.data.ChatData;
import ai.metaheuristic.ai.mhbp.data.ScenarioData;
import ai.metaheuristic.ai.mhbp.events.StoreChatLogEvent;
import ai.metaheuristic.ai.mhbp.provider.ProviderData;
import ai.metaheuristic.ai.mhbp.provider.ProviderQueryService;
import ai.metaheuristic.ai.mhbp.repositories.ApiRepository;
import ai.metaheuristic.ai.mhbp.repositories.ChatRepository;
import ai.metaheuristic.ai.mhbp.yaml.chat.ChatParams;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.S;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static ai.metaheuristic.ai.mhbp.scenario.ScenarioUtils.getNameForVariable;
import static ai.metaheuristic.api.EnumsApi.OperationStatus.OK;

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
    private final ProviderQueryService providerQueryService;
    private final ApplicationEventPublisher eventPublisher;

    public ChatService(@Autowired ChatRepository chatRepository,
                       @Autowired ProviderQueryService providerQueryService,
                       @Autowired ChatTxService chatTxService,
                       @Autowired ApiService apiService,
                       @Autowired ApiRepository apiRepository,
                       @Autowired ApplicationEventPublisher eventPublisher) {
        this.chatRepository = chatRepository;
        this.chatTxService = chatTxService;
        this.apiService = apiService;
        this.apiRepository = apiRepository;
        this.providerQueryService = providerQueryService;
        this.eventPublisher = eventPublisher;
    }

    public ChatData.Chats getChats(Pageable pageable, DispatcherContext context) {
        try {
            List<ChatData.SimpleChat> chats = chatRepository.findIds(pageable, context.getAccountId()).stream()
                    .map(this::to).toList();
            return new ChatData.Chats(new PageImpl<>(chats, pageable, chats.size()));
        }
        catch (Throwable th) {
            log.error("Error:", th);
            return new ChatData.Chats(new PageImpl<>(List.of(), pageable, 0), "Error: " + th.getMessage());
        }
    }

    private ChatData.SimpleChat to(Long id) {
        Chat chat = chatRepository.findById(id).orElseThrow();
        ChatParams params = chat.getChatParams();
        return new ChatData.SimpleChat(chat.id, chat.name, chat.createdOn, new ApiData.ApiUid(params.api.apiId, params.api.code));
    }

    public record ChatInfo(Chat chat, @Nullable Api api, @Nullable String error) {}

    public ChatData.FullChat getChat(Long chatId, DispatcherContext context) {
        ChatInfo chatInfo = getChatInfo(chatId, context);
        ChatParams params = chatInfo.chat.getChatParams();

        ChatData.FullChat fullChat = new ChatData.FullChat();

        if (chatInfo.error!=null) {
            fullChat.addErrorMessage(chatInfo.error);
            return fullChat;
        }
        if (chatInfo.api==null) {
            throw new IllegalStateException("(chatInfo.api==null)");
        }

        fullChat.apiUid = new ApiData.ApiUid(chatInfo.api.id, chatInfo.api.code);
        fullChat.prompts = params.prompts.stream().map(ChatService::to).toList();
        fullChat.chatId = chatId;

        //fullChat.sessionId = null;

        return fullChat;
    }

    private ChatInfo getChatInfo(Long chatId, DispatcherContext context) {
        Chat chat = chatRepository.findById(chatId).orElseThrow();
        ChatParams params = chat.getChatParams();

        if (chat.getAccountId()!=context.getAccountId()) {
            throw new AccessDeniedException("Access denied for chat #" + chatId);
        }
        Api api = apiService.getApi(params.api.apiId, context);
        return new ChatInfo(chat, api, api==null ? "Api not found #" + params.api.apiId : null);
    }

    private static ChatData.ChatPrompt to(ChatParams.Prompt p) {
        return new ChatData.ChatPrompt(S.b(p.p) ? "" : p.p.strip(), S.b(p.a) ? "" : p.a.strip(), S.b(p.r) ? "" : p.r.strip(), null);
    }

    public ChatData.OnePrompt postPrompt(Long chatId, String prompt, DispatcherContext context) {
        ChatData.OnePrompt r = new ChatData.OnePrompt();

        ChatInfo chatInfo = getChatInfo(chatId, context);
        if (chatInfo.error!=null) {
            r.addErrorMessage(chatInfo.error);
            return r;
        }
        if (chatInfo.api==null) {
            throw new IllegalStateException("(chatInfo.api==null)");
        }

        try {
            ChatData.ChatPrompt result = new ChatData.ChatPrompt();
            evaluationAsApiCall(result, new ChatData.PromptEvaluation("n/a", prompt, List.of()), chatInfo.api, context);
            chatTxService.storePrompt(chatId, result);
            eventPublisher.publishEvent(new StoreChatLogEvent(
                    ChatLogService.toChatLogParams(chatInfo.chat.id, null, chatInfo.api, result, context),
                    context.asUserExecContext()));

            r.update(result);
            return r;
        }
        catch (Throwable th) {
            r.error = "373.380 error " + th.getMessage();
            log.error(r.error, th);
            return r;
        }
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

    @SuppressWarnings("UnusedReturnValue")
    public ChatData.ChatPrompt evaluationAsApiCall(ChatData.ChatPrompt r, ChatData.PromptEvaluation se, Api api, DispatcherContext context) {
        String prompt = se.prompt;
        for (ScenarioData.StepVariable variable : se.variables) {
            String varName = getNameForVariable(variable.name);
            String value = variable.value;
            if (value==null) {
                r.error = "373.200 data wasn't found, variable: " + variable.name + ", normalized: " + varName;
                return r;
            }
            prompt = StringUtils.replaceEach(prompt, new String[]{"[[" + variable.name + "]]", "{{" + variable.name + "}}"}, new String[]{value, value});
        }
        r.prompt = prompt;
        log.info("373.240 prompt: {}", prompt);
        ProviderData.QueriedData queriedData = new ProviderData.QueriedData(prompt, context.asUserExecContext());
        ProviderData.QuestionAndAnswer answer = providerQueryService.processQuery(api, queriedData, ProviderQueryService::asQueriedInfoWithError);
        if (answer.status()!=OK) {
            r.error = "373.280 API call error: " + answer.error() + ", prompt: " + prompt;
            return r;
        }
        if (answer.a()==null) {
            r.error = "373.320 answer.a() is null, error: " + answer.error() + ", prompt: " + prompt;
            return r;
        }
        if (answer.a().processedAnswer.answer()==null) {
            r.error = "373.360 processedAnswer.answer() is null, error: " + answer.error() + ", prompt: " + prompt;
            return r;
        }

        r.result = answer.a().processedAnswer.answer();
        r.raw = Objects.requireNonNull(answer.a().processedAnswer.rawAnswerFromAPI().raw());
        return r;
    }
}
