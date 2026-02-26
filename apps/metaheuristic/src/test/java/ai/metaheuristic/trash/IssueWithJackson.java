/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.trash;

import ai.metaheuristic.ai.dispatcher.beans.Batch;
import ai.metaheuristic.api.data.BaseDataClass;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 1/27/2026
 * Time: 8:53 PM
 */
@Execution(ExecutionMode.CONCURRENT)
public class IssueWithJackson {


    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class Bbb extends BaseDataClass {
        public Batch batch;

        @JsonCreator
        public Bbb(
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }

        public Bbb(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public Bbb(Batch batch) {
            this.batch = batch;
        }
    }

    static void main() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        Bbb b = new Bbb();
        b.addErrorMessage("error");

        String json = mapper.writeValueAsString(b);
        System.out.println(json);  // check if errorMessages is present
    }
}
