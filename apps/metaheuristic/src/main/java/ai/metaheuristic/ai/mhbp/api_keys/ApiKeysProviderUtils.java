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
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.mhbp.provider.ProviderData;
import ai.metaheuristic.ai.mhbp.yaml.auth.ApiAuth;
import ai.metaheuristic.ai.yaml.account.AccountParamsYaml;
import ai.metaheuristic.commons.S;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Sergio Lissner
 * Date: 9/10/2023
 * Time: 4:26 AM
 */
public class ApiKeysProviderUtils {

    @Nullable
    public static String getActualToken(ApiAuth.Auth auth, Supplier<Account> accountProviderFunc, Function<String, String> systemEnvFunc ) {
        if (auth.token==null) {
            throw new IllegalStateException("(auth.token==null)");
        }
        if (auth.token.env!=null) {
            return getTokenFromEnvironment(systemEnvFunc, auth.token.env);
        }
        if (auth.token.key!=null) {
            return getTokenFromAccount(accountProviderFunc, auth.token.key);
        }
        return null;
    }

    @Nullable
    private static String getTokenFromAccount(Supplier<Account> accountProviderFunc, String key) {
        Account account = accountProviderFunc.get();
        AccountParamsYaml params = account.getAccountParamsYaml();

        String apiKey = getPredefinedApiKey(key, params);
        if (apiKey!=null) {
            return apiKey;
        }

        return params.apiKeys.stream().filter(o->o.getName().equals(key))
            .map(AccountParamsYaml.ApiKey::getValue)
            .findFirst().orElse(null);
    }

    public static String getTokenFromEnvironment(Function<String, String> systemEnvFunc, String env) {
        String keyName = getEnvParamName(env);
        String token = systemEnvFunc.apply(keyName);
        return token;
    }

    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    @Nullable
    public static String getPredefinedApiKey(String keyName, AccountParamsYaml params) {
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
