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

package ai.metaheuristic.ai.mhbp.data;

import ai.metaheuristic.api.data.BaseDataClass;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 6/21/2023
 * Time: 11:45 PM
 */
public class ChatData {

    public record SimpleChat(Long chatId, String name, long createdOn) {}

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class Chats extends BaseDataClass {
        public List<SimpleChat> chats;

        public Chats(List<SimpleChat> chats) {
            this.chats = chats;
        }
    }


    public record ChatPrompt(int id, String prompt, String answer) {}


    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class FullChat extends BaseDataClass {
        public String sessionId;
        public List<ChatPrompt> prompts;

    }


}
