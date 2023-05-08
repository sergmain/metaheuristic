/*
 *    Copyright 2023, Sergio Lissner, Innovation platforms, LLC
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package ai.metaheuristic.ai.yaml.api.auth;

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
