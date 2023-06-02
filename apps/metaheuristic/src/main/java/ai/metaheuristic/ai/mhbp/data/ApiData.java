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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.mhbp.yaml.auth.ApiAuth;
import ai.metaheuristic.ai.mhbp.yaml.scheme.ApiScheme;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseDataClass;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.domain.Slice;
import org.springframework.lang.Nullable;

import java.util.Collections;

/**
 * @author Sergio Lissner
 * Date: 3/19/2023
 * Time: 9:12 PM
 */
public class ApiData {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleError {
        public String error;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NewApi {
        public String name;
        public String code;
        public String description;
        public Enums.AuthType authType;
        public String username;
        public String password;
        public String token;
        public String url;
        public String text;

        public Enums.AuthType[] authTypes = Enums.AuthType.values();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Error {
        public String error;
        public Enums.QueryResultErrorType errorType;
    }

    // this is a result of querying of info provider
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QueryResult {
        public ApiData.ProcessedAnswerFromAPI processedAnswer;
        public boolean success;

        @Nullable
        @JsonInclude(value= JsonInclude.Include.NON_NULL)
        public Error error;

        public QueryResult(ApiData.ProcessedAnswerFromAPI processedAnswer, boolean success) {
            this.processedAnswer = processedAnswer;
            this.success = success;
        }

        public static QueryResult asError(String error, Enums.QueryResultErrorType errorType) {
            return new QueryResult(null, false, new Error(error, errorType));
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FullQueryResult {
        public QueryResult queryResult;
        public String json;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QueriedInfoWithError {
        public @Nullable NluData.QueriedPrompt queriedInfo;
        public @Nullable Error error;

        public static QueriedInfoWithError asError(String error, Enums.QueryResultErrorType errorType) {
            return new QueriedInfoWithError(null, new Error(error, errorType));
        }
    }

    public record ProcessedAnswerFromAPI(RawAnswerFromAPI rawAnswerFromAPI, @Nullable String answer) {}

    public record RawAnswerFromAPI(Enums.PromptResponseType type, @Nullable String raw, @Nullable byte[] bytes){
        public RawAnswerFromAPI(Enums.PromptResponseType type, String raw) {
            this(type, raw, null);
        }
        public RawAnswerFromAPI(Enums.PromptResponseType type, byte[] bytes) {
            this(type, null, bytes);
        }
    }

    @Data
    @AllArgsConstructor
    public static class SchemeAndParams {
        public ApiScheme scheme;
        public ApiAuth auth;
    }

    @Data
    @AllArgsConstructor
    public static class SchemeAndParamResult {
        public SchemeAndParams schemeAndParams;
        public EnumsApi.OperationStatus status;
        public RawAnswerFromAPI rawAnswerFromAPI;
        public String errorText;
        public int httpCode;

        public SchemeAndParamResult(SchemeAndParams schemeAndParams, String errorText, int httpCode) {
            this.schemeAndParams = schemeAndParams;
            this.status = EnumsApi.OperationStatus.ERROR;
            this.errorText = errorText;
            this.httpCode = httpCode;
        }
    }

    public static class SimpleApi {
        public long id;
        public String name;
        public String code;
        public String scheme;

        public SimpleApi(ai.metaheuristic.ai.mhbp.beans.Api api) {
            super();
            this.id = api.id;
            this.name = api.name;
            this.code = api.code;
            this.scheme = api.getScheme();
        }
    }

    @RequiredArgsConstructor
    public static class Apis extends BaseDataClass {
        public final Slice<SimpleApi> apis;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class Api extends BaseDataClass {
        public SimpleApi api;

        public Api(String errorMessage) {
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public Api(SimpleApi api, String errorMessage) {
            this.api = api;
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public Api(SimpleApi api) {
            this.api = api;
        }
    }
}
