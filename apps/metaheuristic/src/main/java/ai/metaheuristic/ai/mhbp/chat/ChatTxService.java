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
import ai.metaheuristic.ai.mhbp.beans.Chat;
import ai.metaheuristic.ai.mhbp.repositories.ChapterRepository;
import ai.metaheuristic.ai.mhbp.repositories.ChatRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Sergio Lissner
 * Date: 6/21/2023
 * Time: 11:50 PM
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Profile("dispatcher")
public class ChatTxService {

    public final ChatRepository chatRepository;

    @Transactional
    public OperationStatusRest deleteChatById(Long chatId, DispatcherContext context) {
        Chat chat = chatRepository.findById(chatId).orElse(null);
        if (chat==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "134.040 Chat #"+chatId+" wasn't fount");
        }
        chatRepository.delete(chat);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }
}
