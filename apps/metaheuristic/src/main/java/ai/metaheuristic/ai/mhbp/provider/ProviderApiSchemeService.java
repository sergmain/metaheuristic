/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.ai.mhbp.provider;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.mhbp.beans.Api;
import ai.metaheuristic.ai.mhbp.beans.Auth;
import ai.metaheuristic.ai.mhbp.data.ApiData;
import ai.metaheuristic.ai.mhbp.data.CommunicationData;
import ai.metaheuristic.ai.mhbp.data.NluData;
import ai.metaheuristic.ai.mhbp.repositories.AuthRepository;
import ai.metaheuristic.ai.mhbp.api_keys.ApiKeysProvider;
import ai.metaheuristic.commons.yaml.scheme.ApiScheme;
import ai.metaheuristic.ai.utils.HttpUtils;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import com.fasterxml.jackson.core.io.JsonStringEncoder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static ai.metaheuristic.api.EnumsApi.OperationStatus.OK;

/**
 * @author Sergio Lissner
 * Date: 3/19/2023
 * Time: 9:04 PM
 */
@Slf4j
@Service
@Profile("dispatcher")
public class ProviderApiSchemeService {

    private final AuthRepository authRepository;
    private final ApiKeysProvider tokenProvider;
    private final Globals globals;

    public ProviderApiSchemeService(
            @Autowired AuthRepository authRepository,
            @Autowired ApiKeysProvider tokenProvider,
            @Autowired Globals globals) {
        this.authRepository = authRepository;
        this.tokenProvider = tokenProvider;
        this.globals = globals;
    }

    public ApiData.SchemeAndParamResult queryProviders(Api api, ProviderData.QueriedData queriedData, NluData.QueriedPrompt info) {
        ApiScheme scheme = api.getApiScheme();
        List<Auth> auths = authRepository.findAllByCompanyUniqueId(api.companyId);
        Auth auth = auths.stream().filter(o->o.getAuthParams().auth.code.equals(scheme.scheme.auth.code)).findFirst().orElse(null);
        if (auth==null) {
            throw new RuntimeException("Auth wasn't found for code " + scheme.scheme.auth.code);
        }

        ApiData.SchemeAndParams schemeAndParams = new ApiData.SchemeAndParams(scheme, auth.getAuthParams(), ()-> tokenProvider.getActualToken(auth.getAuthParams().auth, queriedData));

        ApiData.SchemeAndParamResult result = queryProviderApi(schemeAndParams, info);
        return result;
    }

    private static CommunicationData.Query buildApiQueryUri(ApiData.SchemeAndParams schemeAndParams, NluData.QueriedPrompt info) {

        if (S.b(schemeAndParams.scheme.scheme.request.uri)) {
            throw new RuntimeException("can't build an URL");
        }
        List<NameValuePair> nvps = new ArrayList<>();
        if (schemeAndParams.scheme.scheme.request.prompt.place== EnumsApi.PromptPlace.uri) {
            nvps.add(new BasicNameValuePair(schemeAndParams.scheme.scheme.request.prompt.param, info.text));
        }

        if (schemeAndParams.auth.auth.type== EnumsApi.AuthType.token) {
            if (schemeAndParams.auth.auth.token==null) {
                throw new RuntimeException("(schemeAndParams.auth.auth.tokenAuth==null)");
            }
            if (schemeAndParams.auth.auth.token.place!= EnumsApi.TokenPlace.header) {
                nvps.add(new BasicNameValuePair(schemeAndParams.auth.auth.token.param, schemeAndParams.auth.auth.token.token));
            }
        }

        return new CommunicationData.Query(schemeAndParams.scheme.scheme.request.uri, nvps);
    }

    @SneakyThrows
    public static ApiData.SchemeAndParamResult queryProviderApi(ApiData.SchemeAndParams schemeAndParams, NluData.QueriedPrompt info) {
        CommunicationData.Query query = buildApiQueryUri(schemeAndParams, info);

        if (query.url==null) {
            return new ApiData.SchemeAndParamResult(schemeAndParams, "url is null", 0);
        }
        if (query.url.indexOf('?')!=-1) {
            return new ApiData.SchemeAndParamResult(schemeAndParams, "params of query must be set via nvps", 0);
        }

        final URIBuilder uriBuilder1 = new URIBuilder(query.url).setCharset(StandardCharsets.UTF_8);
        if (!query.nvps.isEmpty()) {
            uriBuilder1.addParameters(query.nvps);
        }
        final URI uri = uriBuilder1.build();

        final Request request;
        JsonStringEncoder encoder = JsonStringEncoder.getInstance();
        if (schemeAndParams.scheme.scheme.request.type== EnumsApi.HttpMethodType.post) {
            request = Request.post(uri).connectTimeout(Timeout.ofSeconds(5)); //.socketTimeout(20000);
            if (schemeAndParams.scheme.scheme.request.prompt.place== EnumsApi.PromptPlace.text) {
                String encoded = new String(encoder.quoteAsString(info.text));
                String json = schemeAndParams.scheme.scheme.request.prompt.text.replace(schemeAndParams.scheme.scheme.request.prompt.replace, encoded);
                StringEntity entity = new StringEntity(json, StandardCharsets.UTF_8);
                request.body(entity);
            }
        }
        else if (schemeAndParams.scheme.scheme.request.type== EnumsApi.HttpMethodType.get) {
            request = Request.get(uri).connectTimeout(Timeout.ofSeconds(5)); //.socketTimeout(20000);
        }
        else {
            return new ApiData.SchemeAndParamResult(schemeAndParams, "unknown type of request: " + schemeAndParams.scheme.scheme.request.type, 0);
        }

        ApiData.SchemeAndParamResult result = getData( schemeAndParams, request);
        return result;
    }

    @SneakyThrows
    public static ApiData.SchemeAndParamResult getData(ApiData.SchemeAndParams schemeAndParams, final Request request) {
        RestUtils.addHeaders(request);
        final Executor executor;
        if (schemeAndParams.auth.auth.type== EnumsApi.AuthType.basic) {
            if (schemeAndParams.auth.auth.basic==null) {
                return new ApiData.SchemeAndParamResult(schemeAndParams, "(schemeAndParams.params.api.basicAuth==null)", 0);
            }
            executor = getExecutor(schemeAndParams.scheme.scheme.request.uri, schemeAndParams.auth.auth.basic.username, schemeAndParams.auth.auth.basic.password);
        }
        else {
            if (schemeAndParams.auth.auth.token==null) {
                return new ApiData.SchemeAndParamResult(schemeAndParams, "(schemeAndParams.auth.auth.token==null)", 0);
            }
            if (schemeAndParams.auth.auth.token.place== EnumsApi.TokenPlace.header) {
                String token = schemeAndParams.tokenProviderFunc.get();
                request.addHeader(HttpHeaders.AUTHORIZATION, Consts.BEARER + token);
            }
            executor = Executor.newInstance();
        }
        request.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        Response response = executor.execute(request);

        final HttpResponse httpResponse = response.returnResponse();
        if (!(httpResponse instanceof ClassicHttpResponse classicHttpResponse)) {
            throw new IllegalStateException("(!(httpResponse instanceof ClassicHttpResponse classicHttpResponse))");
        }
        final HttpEntity entity = classicHttpResponse.getEntity();
        final int statusCode = classicHttpResponse.getCode();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (entity != null) {
            entity.writeTo(baos);
        }
        byte[] bytes = baos.toByteArray();
        if (statusCode!=HttpStatus.OK.value()) {
            //noinspection
            String d;
            try {
                d = StringUtils.substring(new String(bytes, StandardCharsets.UTF_8), 0, 512);
            }
            catch (Throwable e) {
                d = "<response for API is binary>";
            }
            final String msg = "HttpCode: "+statusCode+", Server response:\n'" + d +"'";
            log.error(msg);
            return new ApiData.SchemeAndParamResult(schemeAndParams, msg, statusCode);
        }
        ApiData.RawAnswerFromAPI rawAnswerFromAPI =
                schemeAndParams.scheme.scheme.response.type.binary
                        ? new ApiData.RawAnswerFromAPI(schemeAndParams.scheme.scheme.response.type, bytes)
                        : new ApiData.RawAnswerFromAPI(schemeAndParams.scheme.scheme.response.type, new String(bytes, StandardCharsets.UTF_8));
        return new ApiData.SchemeAndParamResult(schemeAndParams, OK, rawAnswerFromAPI, null, HttpStatus.OK.value());
    }

    @SuppressWarnings("ConstantValue")
    private static Executor getExecutor(String url, String username, String password) {
        HttpHost httpHost;
        try {
            httpHost = HttpUtils.getHttpHost(url);
        } catch (Throwable th) {
            throw new IllegalArgumentException("Can't build HttpHost for " + url, th);
        }
        if (username == null || password == null) {
            throw new IllegalStateException("(username == null || password == null)");
        }
        return Executor.newInstance().authPreemptive(httpHost).auth(httpHost, username, password.toCharArray());
    }

}
