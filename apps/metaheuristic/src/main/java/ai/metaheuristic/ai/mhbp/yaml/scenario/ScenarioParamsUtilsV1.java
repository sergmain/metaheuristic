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

package ai.metaheuristic.ai.mhbp.yaml.scenario;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

public class ScenarioParamsUtilsV1 extends
        AbstractParamsYamlUtils<ScenarioParamsV1, ScenarioParams, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ScenarioParamsV1.class);
    }

    @Override
    public ScenarioParams upgradeTo(ScenarioParamsV1 v1) {
        v1.checkIntegrity();

        ScenarioParams t = new ScenarioParams();
        t.steps = v1.steps.stream().map(ScenarioParamsUtilsV1::toPrompt).collect(Collectors.toList());
        t.checkIntegrity();
        return t;
    }

    @Nullable
    private static ScenarioParams.Step toPrompt(ScenarioParamsV1.StepV1 v1) {
        ScenarioParams.Step f = new ScenarioParams.Step(
                v1.uuid, v1.parentUuid, v1.name, v1.p, v1.r, v1.resultCode,
                v1.expected, toApi(v1.api), toFunction(v1.function), v1.aggregateType,
                v1.isCachable);
        return f;
    }

    @Nullable
    private static ScenarioParams.Function toFunction(@Nullable ScenarioParamsV1.FunctionV1 v1) {
        if (v1==null) {
            return null;
        }
        return new ScenarioParams.Function(v1.code, v1.context);
    }

    @Nullable
    private static ScenarioParams.Api toApi(@Nullable ScenarioParamsV1.ApiV1 v1) {
        if (v1==null) {
            return null;
        }
        return new ScenarioParams.Api(v1.apiId, v1.code);
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
    public String toString(ScenarioParamsV1 yaml) {
        yaml.checkIntegrity();

        return getYaml().dump(yaml);
    }

    @Override
    public ScenarioParamsV1 to(String s) {
        final ScenarioParamsV1 p = getYaml().load(s);
        return p;
    }

}
