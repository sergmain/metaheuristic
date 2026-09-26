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

package ai.metaheuristic.ai.yaml.exec_context_graph;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;

import org.jspecify.annotations.NonNull;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 3/17/2021
 * Time: 10:47 AM
 */
public class ExecContextGraphParamsUtilsV1
        extends AbstractParamsYamlUtils<
    ExecContextGraphParamsV1, ExecContextGraphParams, Void,
        Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ExecContextGraphParamsV1.class);
    }

    @NonNull
    @Override
    public ExecContextGraphParams upgradeTo(@NonNull ExecContextGraphParamsV1 v1) {
        ExecContextGraphParams t = new ExecContextGraphParams();
        t.graph = v1.graph;
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
    public String toString(@NonNull ExecContextGraphParamsV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ExecContextGraphParamsV1 to(@NonNull String s) {
        final ExecContextGraphParamsV1 p = getYaml().load(s);
        return p;
    }
}
