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

package ai.metaheuristic.ai.mhbp.yaml.auth;

import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.api.data.BaseParams;
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
        // this is a just anon token. it will be used in uri,
        // i.e. https://api.weatherapi.com/v1/current.json?key=xxx&q=94103
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
