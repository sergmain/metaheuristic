/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.yaml.communication.launchpad;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 8/29/2019
 * Time: 6:02 PM
 */
public class LaunchpadCommParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<LaunchpadCommParamsYamlV1, LaunchpadCommParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    public Yaml getYaml() {
        return YamlUtils.init(LaunchpadCommParamsYamlV1.class);
    }

    @Override
    public LaunchpadCommParamsYaml upgradeTo(LaunchpadCommParamsYamlV1 yaml, Long ... vars) {
        LaunchpadCommParamsYaml t = new LaunchpadCommParamsYaml();

        // right now we don't need to convert Graph because if has only one version of structure
        // so just copying of graph field is Ok
        BeanUtils.copyProperties(yaml, t);
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

    public String toString(LaunchpadCommParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    public LaunchpadCommParamsYamlV1 to(String s) {
        final LaunchpadCommParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
