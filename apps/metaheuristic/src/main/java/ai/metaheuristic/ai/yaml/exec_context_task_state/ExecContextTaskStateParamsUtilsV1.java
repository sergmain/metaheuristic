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

import org.jspecify.annotations.NonNull;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 3/17/2021
 * Time: 10:47 AM
 */
public class ExecContextTaskStateParamsUtilsV1
        extends AbstractParamsYamlUtils<
    ExecContextTaskStateParamsV1, ExecContextTaskStateParams, Void,
        Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ExecContextTaskStateParamsV1.class);
    }

    @Override
    public ExecContextTaskStateParams upgradeTo(ExecContextTaskStateParamsV1 v1) {
        ExecContextTaskStateParams t = new ExecContextTaskStateParams();
        t.states.putAll(v1.states);
        t.triesWasMade.putAll(v1.triesWasMade);
        return t;
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
    public String toString(ExecContextTaskStateParamsV1 yaml) {
        return getYaml().dump(yaml);
    }

    @Override
    public ExecContextTaskStateParamsV1 to(String s) {
        final ExecContextTaskStateParamsV1 p = getYaml().load(s);
        return p;
    }
}
