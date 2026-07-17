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

package ai.metaheuristic.ai.dispatcher.settings;

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.account.AccountService;
import ai.metaheuristic.ai.dispatcher.account.AccountTxService;
import ai.metaheuristic.ai.dispatcher.data.AccountData;
import ai.metaheuristic.ai.dispatcher.data.SettingsData;
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsTopLevelService;
import ai.metaheuristic.ai.yaml.account.AccountParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.account.UserContext;
import ai.metaheuristic.commons.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
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

    // key used in DispatcherParamsYaml.metas; value is a json list of enabled locales
    public static final String MH_LANGUAGES = "mh.languages";
    // English is always an enabled/supported language regardless of what is stored
    public static final String LOCALE_EN = "en";

    private final AccountTxService accountTxService;
    private final AccountService accountService;
    private final DispatcherParamsTopLevelService dispatcherParamsTopLevelService;

    public SettingsData.ApiKeys getApiKeys(UserContext context) {
        if (!(context instanceof DispatcherContext dispatcherContext)) {
            return new SettingsData.ApiKeys(List.of("236.040 (!(context instanceof DispatcherContext dispatcherContext))"), List.of());
        }
        // PARAMS lives on the AccountRevision satellite — pull via the composer.
        AccountData.AccountWithRevision composed = accountService.getCurrent(dispatcherContext.account.id);
        AccountParamsYaml params = composed != null ? composed.paramsYaml() : new AccountParamsYaml();

        SettingsData.ApiKeys apiKeys = new SettingsData.ApiKeys(
                params.apiKeys.stream()
                        .map(o->new SettingsData.ApiKey(o.name, o.value))
                        .collect(Collectors.toList()));
        apiKeys.openaiKey = params.openaiKey;
        apiKeys.anthropicKey = params.anthropicKey;

        return apiKeys;
    }

    public OperationStatusRest saveOpenaiKey(String openaiKey, UserContext context) {
        return accountTxService.saveOpenaiKey(context.getAccountId(), context.getCompanyId(), openaiKey);
    }

    public OperationStatusRest saveAnthropicKey(String anthropicKey, UserContext context) {
        return accountTxService.saveAnthropicKey(context.getAccountId(), context.getCompanyId(), anthropicKey);
    }

    public OperationStatusRest changePasswordCommit(String oldPassword, String newPassword, UserContext context) {
        if (StringUtils.isBlank(oldPassword) || StringUtils.isBlank(newPassword)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "236.100 oldPassword and newPassword must not be null");
        }
        return accountTxService.changePasswordCommit(oldPassword, newPassword, context);
    }

    public OperationStatusRest setLanguage(String lang, UserContext context) {
        if (StringUtils.isBlank(lang)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "236.140 Language must not be null");
        }
        return accountTxService.setLanguage(context.getAccountId(), context.getCompanyId(), lang);
    }

    public OperationStatusRest restLanguage(UserContext context) {
        return accountTxService.resetLanguage(context.getAccountId(), context.getCompanyId());
    }

    /**
     * the dispatcher-wide list of supported languages (locales). the returned list always includes English.
     */
    public SettingsData.Languages getLanguages() {
        String json = dispatcherParamsTopLevelService.getMeta(MH_LANGUAGES);
        return new SettingsData.Languages(normalizeLocales(parseLocales(json)));
    }

    /**
     * store the dispatcher-wide list of supported languages. English is always kept enabled.
     * @param locales a json array of locale codes, e.g. ["en","ru"]
     */
    public OperationStatusRest saveLanguages(String locales) {
        List<String> normalized = normalizeLocales(parseLocales(locales));
        // JsonUtils uses jackson 3 whose write is unchecked; serializing a List<String> can't fail here
        dispatcherParamsTopLevelService.putMeta(MH_LANGUAGES, JsonUtils.getMapper().writeValueAsString(normalized));
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    // lower-cases, trims, de-duplicates (keeping order) and always puts English first
    private static List<String> normalizeLocales(List<String> locales) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add(LOCALE_EN);
        for (String locale : locales) {
            if (locale!=null && !locale.isBlank()) {
                set.add(locale.trim().toLowerCase());
            }
        }
        return new ArrayList<>(set);
    }

    private static List<String> parseLocales(@Nullable String json) {
        if (json==null || json.isBlank()) {
            return List.of();
        }
        try {
            return new ArrayList<>(Arrays.asList(JsonUtils.getMapper().readValue(json, String[].class)));
        } catch (RuntimeException e) {
            log.warn("236.180 Can't parse languages json: {}", json, e);
            return List.of();
        }
    }
}
