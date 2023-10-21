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
        public EnumsApi.PromptPlace place;
        public String param;
        public String replace;
        public String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestV2 {
        public EnumsApi.HttpMethodType type;
        public String uri;
        public PromptV2 prompt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseV2 {
        public EnumsApi.PromptResponseType type;
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
