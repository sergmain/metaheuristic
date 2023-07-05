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
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 6/21/2023
 * Time: 11:45 PM
 */
public class ChatData {

    public record SimpleChat(Long chatId, String name, long createdOn, ApiData.ApiUid apiUid) {}

    @Data
    @EqualsAndHashCode(callSuper = false)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ApiForCompany extends BaseDataClass {
        public List<ApiData.ApiUid> apis;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class Chats extends BaseDataClass {
        public Slice<SimpleChat> chats;

        public Chats(Slice<SimpleChat> chats) {
            this.chats = chats;
        }

        public Chats(Slice<SimpleChat> chats, String error) {
            this.chats = chats;
            addErrorMessage(error);
        }

        @JsonCreator
        public Chats(
                @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
                @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class OnePrompt extends BaseDataClass {
        public String prompt;
        public String result;
        public String raw;
        @Nullable
        public  String error;

        @JsonCreator
        public OnePrompt(
                @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
                @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }

        public void update(ChatPrompt chatPrompt) {
            this.prompt = chatPrompt.prompt;
            this.result = chatPrompt.result;
            this.raw = chatPrompt.raw;
            this.error = chatPrompt.error;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class FullChat extends BaseDataClass {
        public String sessionId;
        public Long chatId;
        public ApiData.ApiUid apiUid;
        public List<ChatPrompt> prompts;

        @JsonCreator
        public FullChat(
                @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
                @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChatPrompt {
        public String prompt;
        public String result;
        public String raw;
        @Nullable
        public String error;
    }

    @Data
    @AllArgsConstructor
    public static class PromptEvaluation {
        public String uuid;
        public String prompt;
        public List<ScenarioData.StepVariable> variables;
    }
}
