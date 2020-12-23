/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

package ai.metaheuristic.ai.json;

import ai.metaheuristic.ai.utils.JsonUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 10/21/2020
 * Time: 9:27 AM
 */
public class TestJsonMarshaling {

    private static final String JSON = "{\"errorMessages\":[\"#295.230 Fatal error - file is null \"],\"infoMessages\":null,\"status\":\"ERROR\",\"errorMessagesAsList\":[\"#295.230 Fatal error - file is null \"],\"errorMessagesAsStr\":\"#295.230 Fatal error - file is null \",\"infoMessagesAsList\":[]}";


    @Data
    @JsonInclude
    public static class BaseClass {

        @JsonInclude(value= JsonInclude.Include.NON_NULL, content= JsonInclude.Include.NON_EMPTY)
        @Nullable
        public List<String> errorMessages;

        @JsonInclude(value= JsonInclude.Include.NON_NULL, content= JsonInclude.Include.NON_EMPTY)
        @Nullable
        public List<String> infoMessages;

        public void addErrorMessage(String errorMessage) {
            if (this.errorMessages==null) {
                this.errorMessages = new ArrayList<>();
            }
            this.errorMessages.add(errorMessage);
        }

        public void addErrorMessages(@NonNull List<String> errorMessages) {
            if (this.errorMessages==null) {
                this.errorMessages = new ArrayList<>();
            }
            this.errorMessages.addAll(errorMessages);
        }

        public void addInfoMessage(String infoMessage) {
            if (this.infoMessages==null) {
                this.infoMessages = new ArrayList<>();
            }
            this.infoMessages.add(infoMessage);
        }

        @JsonIgnore
        public @NonNull String getErrorMessagesAsStr() {
            if (!isNotEmpty(errorMessages)) {
                return "";
            }
            if (errorMessages.size()==1) {
                return Objects.requireNonNull(errorMessages.get(0));
            }
            return Objects.requireNonNull(errorMessages.toString());
        }

        @JsonIgnore
        public @NonNull List<String> getErrorMessagesAsList() {
            return isNotEmpty(errorMessages) ? errorMessages : List.of();
        }

        @JsonIgnore
        public @NonNull List<String> getInfoMessagesAsList() {
            return isNotEmpty(infoMessages) ? infoMessages : List.of();
        }

        @JsonIgnore
        public boolean isErrorMessages() {
            return isNotEmpty(errorMessages);
        }

        @JsonIgnore
        public boolean isInfoMessages() {
            return isNotEmpty(infoMessages);
        }

        @JsonIgnore
        private static boolean isNotEmpty(@Nullable Collection<?> collection) {
            return collection!=null && !collection.isEmpty();
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class StatusClass extends BaseClass {

        public EnumsApi.OperationStatus status;

        public StatusClass(EnumsApi.OperationStatus status) {
            this.status = status;
        }

        public StatusClass(EnumsApi.OperationStatus status, List<String> errorMessages) {
            this.status = status;
            this.errorMessages = errorMessages;
        }

        @JsonCreator
        public StatusClass(@JsonProperty("status") EnumsApi.OperationStatus status,
                           @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
                           @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.status = status;
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }

        public StatusClass(EnumsApi.OperationStatus status, String errorMessage) {
            this.status = status;
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public StatusClass(EnumsApi.OperationStatus status, @Nullable String infoMessage, @Nullable String errorMessage) {
            this.status = status;
            if (!S.b(infoMessage)) {
                this.infoMessages = List.of(infoMessage);
            }
            if (!S.b(errorMessage)) {
                this.errorMessages = List.of(errorMessage);
            }
        }

    }

    @Test
    public void test() throws JsonProcessingException {
        StatusClass rest = JsonUtils.getMapper().readValue(JSON, StatusClass.class);

        assertNotNull(rest);
        assertNotNull(rest.getErrorMessages());
        assertEquals(1, rest.getErrorMessages().size());

        //

        StatusClass status = new StatusClass(EnumsApi.OperationStatus.ERROR, "error");

        String s = JsonUtils.getMapper().writeValueAsString(status);
        rest = JsonUtils.getMapper().readValue(s, StatusClass.class);

        assertNotNull(rest);
        assertEquals(EnumsApi.OperationStatus.ERROR, rest.status);
        assertNotNull(rest.getErrorMessages());
        assertEquals(1, rest.getErrorMessages().size());
        assertEquals("error", rest.getErrorMessages().get(0));

        //

        status = new StatusClass(EnumsApi.OperationStatus.OK);

        s = JsonUtils.getMapper().writeValueAsString(status);
        assertFalse(s.contains("errorMessages"));
        assertFalse(s.contains("infoMessages"));

        rest = JsonUtils.getMapper().readValue(s, StatusClass.class);
        assertNotNull(rest);
        assertEquals(EnumsApi.OperationStatus.OK, rest.status);
        assertNull(rest.getErrorMessages());
        assertNull(rest.getInfoMessages());

    }
}
