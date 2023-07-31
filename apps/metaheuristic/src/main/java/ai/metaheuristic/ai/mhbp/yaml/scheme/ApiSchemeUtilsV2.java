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

package ai.metaheuristic.ai.mhbp.yaml.scheme;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.yaml.snakeyaml.Yaml;

@SuppressWarnings("ConstantValue")
public class ApiSchemeUtilsV2 extends
        AbstractParamsYamlUtils<ApiSchemeV2, ApiScheme, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ApiSchemeV2.class);
    }

    @Override
    public ApiScheme upgradeTo(ApiSchemeV2 v2) {
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
    public String toString(ApiSchemeV2 yaml) {
        yaml.checkIntegrity();

        return getYaml().dump(yaml);
    }

    @Override
    public ApiSchemeV2 to(String s) {
        final ApiSchemeV2 p = getYaml().load(s);
        return p;
    }

}
