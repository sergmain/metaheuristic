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

package ai.metaheuristic.commons.yaml.auth;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.Yaml;

public class ApiAuthUtilsV1 extends
        AbstractParamsYamlUtils<ApiAuthV1, ApiAuthV2, ApiAuthUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ApiAuthV1.class);
    }

    @Override
    public ApiAuthV2 upgradeTo(ApiAuthV1 v1) {
        v1.checkIntegrity();

        ApiAuthV2 t = new ApiAuthV2();
        t.auth.code = v1.auth.code;
        t.auth.type = v1.auth.type;
        t.auth.basic = toBasicAuth(v1.auth.basic);
        t.auth.token = toTokenAuth(v1.auth.token);

        t.checkIntegrity();
        return t;
    }

    @Nullable
    public static ApiAuthV2.BasicAuthV2 toBasicAuth(@Nullable ApiAuthV1.BasicAuthV1 v1) {
        if (v1==null) {
            return null;
        }
        ApiAuthV2.BasicAuthV2 ta = new ApiAuthV2.BasicAuthV2(v1.username, v1.password);
        return ta;
    }

    @Nullable
    public static ApiAuthV2.TokenAuthV2 toTokenAuth(@Nullable ApiAuthV1.TokenAuthV1 v1) {
        if (v1==null) {
            return null;
        }
        ApiAuthV2.TokenAuthV2 ta = new ApiAuthV2.TokenAuthV2(v1.place, v1.token, v1.param, v1.env, null);
        return ta;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        return null;
    }

    @Override
    public ApiAuthUtilsV2 nextUtil() {
        return (ApiAuthUtilsV2) ApiAuthUtils.UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(ApiAuthV1 yaml) {
        yaml.checkIntegrity();

        return getYaml().dump(yaml);
    }

    @Override
    public ApiAuthV1 to(String s) {
        final ApiAuthV1 p = getYaml().load(s);
        return p;
    }

}
