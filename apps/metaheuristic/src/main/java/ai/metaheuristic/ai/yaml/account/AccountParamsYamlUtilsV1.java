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

package ai.metaheuristic.ai.yaml.account;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Sergio Lissner
 * Date: 7/15/2023
 * Time: 12:01 AM
 */
public class AccountParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<AccountParamsYamlV1, AccountParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(AccountParamsYamlV1.class);
    }

    @NonNull
    @Override
    public AccountParamsYaml upgradeTo(@NonNull AccountParamsYamlV1 v1) {
        v1.checkIntegrity();
        AccountParamsYaml t = new AccountParamsYaml();
        v1.apiKeys.stream().map(AccountParamsYamlUtilsV1::to).collect(Collectors.toCollection(()->t.apiKeys));
        t.openaiKey = v1.openaiKey;

        t.checkIntegrity();
        return t;
    }

    public static AccountParamsYaml.ApiKey to(AccountParamsYamlV1.ApiKeyV1 v1) {
        return new AccountParamsYaml.ApiKey(v1.name, v1.value);
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        // not supported
        return null;
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        // not supported
        return null;
    }

    @Override
    public String toString(@NonNull AccountParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public AccountParamsYamlV1 to(@NonNull String s) {
        final AccountParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
