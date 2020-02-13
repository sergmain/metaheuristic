/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.yaml.exec_context;

import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYamlV1;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class ExecContextParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<ExecContextParamsYamlV1, ExecContextParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ExecContextParamsYamlV1.class);
    }

    @Override
    public ExecContextParamsYaml upgradeTo(ExecContextParamsYamlV1 yaml, Long ... vars) {
        ExecContextParamsYaml t = new ExecContextParamsYaml();

        // right now we don't need to convert Graph because it has only one version of structure
        // so just copying of graph field is Ok
        BeanUtils.copyProperties(yaml.execContextYaml, t.execContextYaml);
        if (yaml.execContextYaml.variables!=null) {
            t.execContextYaml.variables.putAll(yaml.execContextYaml.variables);
        }
        t.graph = yaml.graph;
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
    public String toString(ExecContextParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @Override
    public ExecContextParamsYamlV1 to(String s) {
        final ExecContextParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
