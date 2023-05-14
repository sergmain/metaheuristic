/*
 *    Copyright 2023, Sergio Lissner, Innovation platforms, LLC
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package ai.metaheuristic.ai.mhbp.yaml.scenario;

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

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiV1 {
        public long apiId;
        // stored just for info
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

        @Nullable
        public ApiV1 api;

        @Nullable
        public FunctionV1 function;
    }

    public List<StepV1> steps = new ArrayList<>();
}
