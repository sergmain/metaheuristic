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

package ai.metaheuristic.commons.yaml.auth;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

@SuppressWarnings("FieldMayBeStatic")
@Data
public class ApiAuthV1 implements BaseParams  {

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
    public static class BasicAuthV1 {
        public String username;
        public String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenAuthV1 {
        public EnumsApi.TokenPlace place;
        public String token;
        public String param;
        public String env;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthV1 {
        public String code;
        public EnumsApi.AuthType type;

        @Nullable
        public BasicAuthV1 basic;

        @Nullable
        public TokenAuthV1 token;
    }

    public final AuthV1 auth = new AuthV1();
}
