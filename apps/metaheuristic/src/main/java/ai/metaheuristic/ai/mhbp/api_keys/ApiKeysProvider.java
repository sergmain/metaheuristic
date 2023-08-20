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

package ai.metaheuristic.ai.mhbp.api_keys;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.repositories.AccountRepository;
import ai.metaheuristic.ai.mhbp.provider.ProviderData;
import ai.metaheuristic.ai.mhbp.yaml.auth.ApiAuth;
import ai.metaheuristic.ai.yaml.account.AccountParamsYaml;
import ai.metaheuristic.commons.S;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * @author Sergio Lissner
 * Date: 7/14/2023
 * Time: 11:38 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
public class ApiKeysProvider {

    private final Globals globals;
    private final AccountRepository accountRepository;

    public ApiKeysProvider(@Autowired Globals globals, @Autowired AccountRepository accountRepository) {
        this.globals = globals;
        this.accountRepository = accountRepository;
    }

    @Nullable
    public String getActualToken(ApiAuth.Auth auth, ProviderData.QueriedData queriedData) {
        if (auth.token==null) {
            throw new IllegalStateException("(auth.token==null)");
        }

        Account account = accountRepository.findById(queriedData.userExecContext().accountId()).orElseThrow();
        AccountParamsYaml params = account.getAccountParamsYaml();

        String keyName = getEnvParamName(auth.token.env);

        String apiKey = getPredefindedApiKey(keyName, params);
        if (apiKey!=null) {
            return apiKey;
        }

        return params.apiKeys.stream().filter(o->o.getName().equals(keyName))
                .map(AccountParamsYaml.ApiKey::getValue)
                .findFirst().orElse(null);

/*
        if (Arrays.stream(StringUtils.split(globals.activeProfiles, ", "))
                .anyMatch(o->o.contains(Consts.STANDALONE_PROFILE))) {
            Account account = accountRepository.findById(queriedData.userExecContext().accountId()).orElseThrow();
            AccountParamsYaml params = account.getAccountParamsYaml();

            String evnParam = getEnvParamName(auth.token.env);

            return params.apiKeys.stream().filter(o->o.getName().equals(evnParam))
                    .map(AccountParamsYaml.ApiKey::getValue)
                    .findFirst().orElse(null);
        }
        else {
            if (auth.token.token!=null) {
                return auth.token.token;
            }
            String evnParam = getEnvParamName(auth.token.env);
            final String value = System.getenv(evnParam);
            if (S.b(value)) {
                throw new RuntimeException("(S.b(value)) , evn param: " + evnParam);
            }
            return value;

        }
*/
    }

    @Nullable
    public static String getPredefindedApiKey(String keyName, AccountParamsYaml params) {
        return switch(keyName) {
            case Consts.OPENAI_API_KEY: yield params.openaiKey;
            default: yield null;
        };
    }

    public static String getEnvParamName(String env) {
        if (S.b(env)) {
            throw new IllegalStateException("(S.b(env))");
        }
        int start = 0;
        int end = 0;
        if (StringUtils.startsWithAny(env, "%", "$")) {
            ++start;
        }
        if (StringUtils.endsWithAny(env, "%", "$")) {
            ++end;
        }
        return env.substring(start, env.length()-end);
    }

}
