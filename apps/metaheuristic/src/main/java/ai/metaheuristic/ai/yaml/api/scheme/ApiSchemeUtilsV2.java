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

package ai.metaheuristic.ai.yaml.api.scheme;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

@SuppressWarnings("ConstantValue")
public class ApiSchemeUtilsV2 extends
        AbstractParamsYamlUtils<ApiSchemeV2, ApiScheme, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ApiSchemeV2.class);
    }

    @NonNull
    @Override
    public ApiScheme upgradeTo(@NonNull ApiSchemeV2 v2) {
        v2.checkIntegrity();

        ApiScheme t = new ApiScheme();
        t.code = v2.code;
        t.scheme.auth = toAuth(v2.scheme.auth);
        t.scheme.request = toRequest(v2.scheme.request);
        t.scheme.response = toResponse(v2.scheme.response);

        t.checkIntegrity();
        return t;
    }

    private static ApiScheme.Auth toAuth(ApiSchemeV2.AuthV2 v2) {
        if (v2==null) {
            throw new IllegalStateException("(v2==null)");
        }
        ApiScheme.Auth a = new ApiScheme.Auth();
        a.code = v2.code;
        return a;
    }

    private static ApiScheme.Request toRequest(ApiSchemeV2.RequestV2 v2) {
        ApiScheme.Request r = new ApiScheme.Request();
        r.type = v2.type;
        r.uri = v2.uri;
        r.prompt = toPrompt(v2.prompt);
        return r;
    }

    private static ApiScheme.Prompt toPrompt(ApiSchemeV2.PromptV2 v2) {
        ApiScheme.Prompt r = new ApiScheme.Prompt();
        r.place = v2.place;
        r.replace = v2.replace;
        r.text = v2.text;
        r.param = v2.param;
        return r;
    }

    private static ApiScheme.Response toResponse(ApiSchemeV2.ResponseV2 v2) {
        ApiScheme.Response r = new ApiScheme.Response();
        r.type = v2.type;
        r.path = v2.path;
        return r;
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
    public String toString(@NonNull ApiSchemeV2 yaml) {
        yaml.checkIntegrity();

        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ApiSchemeV2 to(@NonNull String s) {
        final ApiSchemeV2 p = getYaml().load(s);
        return p;
    }

}
