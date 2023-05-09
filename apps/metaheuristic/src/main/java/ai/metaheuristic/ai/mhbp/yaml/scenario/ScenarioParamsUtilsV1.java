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
        ScenarioParams.Step f = new ScenarioParams.Step(v1.uuid, v1.name, v1.p, v1.a, v1.apiId, v1.apiCode);
        return f;
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
