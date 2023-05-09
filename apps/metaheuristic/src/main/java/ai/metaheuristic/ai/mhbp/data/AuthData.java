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

package ai.metaheuristic.ai.mhbp.data;

import ai.metaheuristic.api.data.BaseDataClass;
import lombok.*;
import org.springframework.data.domain.Slice;

import java.util.Collections;

/**
 * @author Sergio Lissner
 * Date: 4/13/2023
 * Time: 12:09 AM
 */
public class AuthData {

    public static class SimpleAuth {
        public long id;
        public String code;
        public String params;

        public SimpleAuth(ai.metaheuristic.ai.mhbp.beans.Auth auth) {
            this.id = auth.id;
            this.code = auth.code;
            this.params = auth.getParams();
        }
    }

    @RequiredArgsConstructor
    public static class Auths extends BaseDataClass {
        public final Slice<SimpleAuth> auths;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class Auth extends BaseDataClass {
        public SimpleAuth auth;

        public Auth(String errorMessage) {
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public Auth(SimpleAuth auth, String errorMessage) {
            this.auth = auth;
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public Auth(SimpleAuth auth) {
            this.auth = auth;
        }
    }

}
