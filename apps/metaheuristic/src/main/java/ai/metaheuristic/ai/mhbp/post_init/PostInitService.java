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

package ai.metaheuristic.ai.mhbp.post_init;

import ai.metaheuristic.ai.mhbp.beans.Api;
import ai.metaheuristic.ai.mhbp.beans.Auth;
import ai.metaheuristic.ai.mhbp.repositories.ApiRepository;
import ai.metaheuristic.ai.mhbp.repositories.AuthRepository;
import ai.metaheuristic.ai.sec.CustomUserDetails;
import ai.metaheuristic.commons.yaml.auth.ApiAuth;
import ai.metaheuristic.commons.yaml.auth.ApiAuthUtils;
import ai.metaheuristic.commons.yaml.scheme.ApiScheme;
import ai.metaheuristic.commons.yaml.scheme.ApiSchemeUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author Sergio Lissner
 * Date: 2/11/2024
 * Time: 4:10 PM
 */
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class PostInitService {

    private final ApiRepository apiRepository;
    private final AuthRepository authRepository;

    public static final String APIS_PREFIX = "/mhbp/mh-apis/";
    public static final String AUTHS_PREFIX = "/mhbp/mh-auths/";

    private static final String[] APIS = {
        "mh.openai-dall-e-256x256",
        "mh.openai-gpt-3.5-turbo:1.0",
        "mh.openai-davinci-003:1.0"
    };

    private static final String[] AUTHS = {
        "mh.openai"
    };

    @PostConstruct
    public void post() {
        log.warn("AT THIS MOMENT INITIALIZING OF AUTH FOR LLM PROVIDERS, IS DISABLED");
//        initAuths();
//        initApis();
    }

    private void initApis() {
        for (String apiCode : APIS) {
            try {
                Api api = apiRepository.findByApiCode(apiCode);
                if (api!=null) {
                    continue;
                }
                String resource = APIS_PREFIX + apiCode+".yaml";
                String content = IOUtils.resourceToString(resource, StandardCharsets.UTF_8);
                ApiScheme apiScheme = ApiSchemeUtils.UTILS.to(content);

                api = new Api();
                api.name = apiScheme.code;
                api.code = apiScheme.code;
                api.setScheme(content);
                api.createdOn = System.currentTimeMillis();
                api.companyId = 1L;
                api.accountId = CustomUserDetails.MAIN_USER_ID;
                api.disabled = false;

                apiRepository.save(api);

            } catch (IOException e) {
                log.error("150.090 error", e);
            }
        }
    }

    private void initAuths() {
        for (String authCode : AUTHS) {
            try {
                Auth auth = authRepository.findByCode(authCode);
                if (auth!=null) {
                    continue;
                }
                String resource = AUTHS_PREFIX + authCode;
                String content = IOUtils.resourceToString(resource, StandardCharsets.UTF_8);
                ApiAuth apiAuth = ApiAuthUtils.UTILS.to(content);

                auth = new Auth();
                auth.code = apiAuth.auth.code;
                auth.setParams(content);
                auth.createdOn = System.currentTimeMillis();
                auth.companyId = 1L;
                auth.accountId = CustomUserDetails.MAIN_USER_ID;
                auth.disabled = false;

                authRepository.save(auth);

            } catch (IOException e) {
                log.error("150.120 error", e);
            }

        }
    }
}
