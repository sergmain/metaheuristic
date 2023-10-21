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

package ai.metaheuristic.commons.yaml.scheme;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.commons.S;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

@SuppressWarnings("FieldMayBeStatic")
@Data
public class ApiScheme implements BaseParams {

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
    public static class Auth {
        public String code;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Prompt {
        public EnumsApi.PromptPlace place;
        public String param;
        public String replace;
        public String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        public EnumsApi.HttpMethodType type;
        public String uri;
        public Prompt prompt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        public EnumsApi.PromptResponseType type;
        @Nullable
        public String path;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Scheme {
        public Auth auth;
        public Request request;
        public Response response;
    }

    public String code;
    public final Scheme scheme = new Scheme();
}
