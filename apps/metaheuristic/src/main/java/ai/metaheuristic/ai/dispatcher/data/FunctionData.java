/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.data;

import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.data.SimpleSelectOption;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class FunctionData {


    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class FunctionsResult extends BaseDataClass {
        public List<Function> functions;
        public EnumsApi.DispatcherAssetMode assetMode;

        @JsonCreator
        public FunctionsResult(
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }
    }

    @Data
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class SimpleFunctionResult extends BaseDataClass {
        public Long id;
        public String code;
        public String type;
        public String params;

        @JsonCreator
        public SimpleFunctionResult(
            @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
            @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
            this.errorMessages = errorMessages;
            this.infoMessages = infoMessages;
        }
    }

    @Data
    public static class FunctionResult {
        public List<SimpleSelectOption> selectOptions = new ArrayList<>();
        public List<String> functions = new ArrayList<>();

    }

    @Data
    @AllArgsConstructor
    public static class FunctionCode {
        public Long id;
        public String functionCode;
    }
}
