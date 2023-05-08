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

package ai.metaheuristic.ai.yaml.api.auth;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

@SuppressWarnings("FieldMayBeStatic")
@Data
public class ApiAuth implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        if (auth.basic==null && auth.token==null) {
            throw new CheckIntegrityFailedException("(api.basicAuth==null && api.tokenAuth==null)");
        }
        if (auth.token!=null && auth.token.token==null && auth.token.env==null) {
            throw new CheckIntegrityFailedException("(auth.token!=null && auth.token.token==null && auth.token.env==null)");
        }
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BasicAuth {
        public String username;
        public String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenAuth {
        public Enums.TokenPlace place;
        public String token;
        public String param;
        public String env;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Auth {
        public String code;
        public Enums.AuthType type;
        @Nullable
        public BasicAuth basic;

        @Nullable
        public TokenAuth token;
    }

    public final Auth auth = new Auth();
}
