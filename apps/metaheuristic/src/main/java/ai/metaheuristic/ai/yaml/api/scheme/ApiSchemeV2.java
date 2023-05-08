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

package ai.metaheuristic.ai.yaml.api.scheme;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

@SuppressWarnings("FieldMayBeStatic")
@Data
public class ApiSchemeV2 implements BaseParams {

    public final int version=2;

    @Override
    public boolean checkIntegrity() {
        if (scheme.auth==null || S.b(scheme.auth.code)) {
            throw new CheckIntegrityFailedException("(scheme.auth==null || S.b(scheme.auth.code))");
        }
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthV2 {
        public String code;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptV2 {
        public Enums.PromptPlace place;
        public String param;
        public String replace;
        public String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestV2 {
        public Enums.HttpMethodType type;
        public String uri;
        public PromptV2 prompt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseV2 {
        public Enums.PromptResponseType type;
        @Nullable
        public String path;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SchemeV2 {
        public AuthV2 auth;
        public RequestV2 request;
        public ResponseV2 response;
    }

    public String code;
    public final SchemeV2 scheme = new SchemeV2();
}
