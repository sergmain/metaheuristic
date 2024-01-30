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

public class ApiAuthUtilsV2 extends
        AbstractParamsYamlUtils<ApiAuthV2, ApiAuth, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ApiAuthV2.class);
    }

    @Override
    public ApiAuth upgradeTo(ApiAuthV2 v2) {
        v2.checkIntegrity();

        ApiAuth t = new ApiAuth();
        t.auth.code = v2.auth.code;
        t.auth.type = v2.auth.type;
        t.auth.basic = toBasicAuth(v2.auth.basic);
        t.auth.token = toTokenAuth(v2.auth.token);

        t.checkIntegrity();
        return t;
    }

    @Nullable
    public static ApiAuth.BasicAuth toBasicAuth(@Nullable ApiAuthV2.BasicAuthV2 v2) {
        if (v2==null) {
            return null;
        }
        ApiAuth.BasicAuth ta = new ApiAuth.BasicAuth(v2.username, v2.password);
        return ta;
    }

    @Nullable
    public static ApiAuth.TokenAuth toTokenAuth(@Nullable ApiAuthV2.TokenAuthV2 v2) {
        if (v2==null) {
            return null;
        }
        ApiAuth.TokenAuth ta = new ApiAuth.TokenAuth(v2.place, v2.token, v2.param, v2.env, v2.key);
        return ta;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        return null;
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(ApiAuthV2 yaml) {
        yaml.checkIntegrity();

        return getYaml().dump(yaml);
    }

    @Override
    public ApiAuthV2 to(String s) {
        final ApiAuthV2 p = getYaml().load(s);
        return p;
    }

}
