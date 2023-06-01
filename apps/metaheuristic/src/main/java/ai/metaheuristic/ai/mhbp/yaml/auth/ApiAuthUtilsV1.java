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

package ai.metaheuristic.ai.mhbp.yaml.auth;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.Yaml;

public class ApiAuthUtilsV1 extends
        AbstractParamsYamlUtils<ApiAuthV1, ApiAuth, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ApiAuthV1.class);
    }

    @NonNull
    @Override
    public ApiAuth upgradeTo(@NonNull ApiAuthV1 v1) {
        v1.checkIntegrity();

        ApiAuth t = new ApiAuth();
        t.auth.code = v1.auth.code;
        t.auth.type = v1.auth.type;
        t.auth.basic = toBasicAuth(v1.auth.basic);
        t.auth.token = toTokenAuth(v1.auth.token);

        t.checkIntegrity();
        return t;
    }

    @Nullable
    public static ApiAuth.BasicAuth toBasicAuth(@Nullable ApiAuthV1.BasicAuthV1 v1) {
        if (v1==null) {
            return null;
        }
        ApiAuth.BasicAuth ta = new ApiAuth.BasicAuth(v1.username, v1.password);
        return ta;
    }

    @Nullable
    public static ApiAuth.TokenAuth toTokenAuth(@Nullable ApiAuthV1.TokenAuthV1 v1) {
        if (v1==null) {
            return null;
        }
        ApiAuth.TokenAuth ta = new ApiAuth.TokenAuth(v1.place, v1.token, v1.param, v1.env);
        return ta;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
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
    public String toString(@NonNull ApiAuthV1 yaml) {
        yaml.checkIntegrity();

        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ApiAuthV1 to(@NonNull String s) {
        final ApiAuthV1 p = getYaml().load(s);
        return p;
    }

}
