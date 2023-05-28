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

package ai.metaheuristic.ai.mhbp.yaml.scenario;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

public class ScenarioParamsUtilsV1 extends
        AbstractParamsYamlUtils<ScenarioParamsV1, ScenarioParams, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ScenarioParamsV1.class);
    }

    @NonNull
    @Override
    public ScenarioParams upgradeTo(@NonNull ScenarioParamsV1 v1) {
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
                v1.expected, toApi(v1.api), toFunction(v1.function), v1.aggregateType);
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
    public String toString(@NonNull ScenarioParamsV1 yaml) {
        yaml.checkIntegrity();

        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ScenarioParamsV1 to(@NonNull String s) {
        final ScenarioParamsV1 p = getYaml().load(s);
        return p;
    }

}
