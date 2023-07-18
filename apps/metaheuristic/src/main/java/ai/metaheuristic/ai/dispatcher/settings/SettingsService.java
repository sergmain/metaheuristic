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

package ai.metaheuristic.ai.dispatcher.settings;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.account.AccountTxService;
import ai.metaheuristic.ai.dispatcher.data.SettingsData;
import ai.metaheuristic.ai.yaml.account.AccountParamsYaml;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * @author Sergio Lissner
 * Date: 7/17/2023
 * Time: 11:17 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class SettingsService {

    private final AccountTxService accountTxService;

    public SettingsData.ApiKeys getApiKeys(DispatcherContext context) {
        AccountParamsYaml params = context.account.getAccountParamsYaml();

        SettingsData.ApiKeys apiKeys = new SettingsData.ApiKeys(
                params.apiKeys.stream()
                        .map(o->new SettingsData.ApiKey(o.name, o.value))
                        .collect(Collectors.toList()));
        apiKeys.openaiKey = params.openaiKey;

        return apiKeys;
    }

    public OperationStatusRest saveOpenaiKey(String openaiKey, DispatcherContext context) {
        return accountTxService.saveOpenaiKey(context.getAccountId(), context.getCompanyId(), openaiKey);
    }
}
