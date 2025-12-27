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

package ai.metaheuristic.ai.mhbp.yaml.scenario;

import ai.metaheuristic.ai.dispatcher.internal_functions.aggregate.AggregateFunction;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("FieldMayBeStatic")
@Data
public class ScenarioParamsV1 implements BaseParams {

    public final int version=1;

    @SuppressWarnings("RedundantMethodOverride")
    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiV1 {
        public long apiId;
        public String code;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionV1 {
        public String code;
        public EnumsApi.FunctionExecContext context = EnumsApi.FunctionExecContext.internal;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepV1 {
        public String uuid;
        @Nullable
        public String parentUuid;
        public String name;
        // prompt or inputCode fro function
        public String p;
        // result of executing
        public String r;
        public String resultCode;

        // this field is for acceptance-test function only. in future, it'll be changed to meta, maybe.
        @Nullable
        public String expected;

        @Nullable
        public ApiV1 api;

        @Nullable
        public FunctionV1 function;

        @Nullable
        public AggregateFunction.AggregateType aggregateType;

        public boolean isCachable = false;
    }

    public List<StepV1> steps = new ArrayList<>();
}
