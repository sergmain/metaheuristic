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
public class ScenarioParams implements BaseParams {

    public final int version=1;

    @SuppressWarnings("RedundantMethodOverride")
    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Api {
        public long apiId;
        // stored just for info
        public String code;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Function {
        public String code;
        public EnumsApi.FunctionExecContext context = EnumsApi.FunctionExecContext.internal;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Step {
        public String uuid;
        @Nullable
        public String parentUuid;
        public String name;
        // prompt or inputCode for function
        public String p;
        // result of executing
        public String r;
        public String resultCode;

        // this field is for mh.acceptance-test function only. in future, it'll be changed to meta, maybe.
        @Nullable
        public String expected;

        @Nullable
        public Api api;

        @Nullable
        public Function function;

        @Nullable
        public AggregateFunction.AggregateType aggregateType;

        public boolean isCachable = false;
    }

    public List<Step> steps = new ArrayList<>();
}
