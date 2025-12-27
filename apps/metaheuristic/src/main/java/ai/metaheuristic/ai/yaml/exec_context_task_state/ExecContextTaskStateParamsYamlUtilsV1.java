/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.yaml.exec_context_task_state;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 3/17/2021
 * Time: 10:47 AM
 */
public class ExecContextTaskStateParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<
        ExecContextTaskStateParamsYamlV1, ExecContextTaskStateParamsYaml, Void,
        Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ExecContextTaskStateParamsYamlV1.class);
    }

    @NonNull
    @Override
    public ExecContextTaskStateParamsYaml upgradeTo(@NonNull ExecContextTaskStateParamsYamlV1 v1) {
        ExecContextTaskStateParamsYaml t = new ExecContextTaskStateParamsYaml();
        t.states.putAll(v1.states);
        t.triesWasMade.putAll(v1.triesWasMade);
        return t;
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
    public String toString(@NonNull ExecContextTaskStateParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ExecContextTaskStateParamsYamlV1 to(@NonNull String s) {
        final ExecContextTaskStateParamsYamlV1 p = getYaml().load(s);
        return p;
    }
}
