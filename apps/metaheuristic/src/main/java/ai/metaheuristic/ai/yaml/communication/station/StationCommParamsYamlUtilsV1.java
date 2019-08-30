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

package ai.metaheuristic.ai.yaml.communication.station;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 8/29/2019
 * Time: 6:02 PM
 */
public class StationCommParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<StationCommParamsYamlV1, StationCommParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    public Yaml getYaml() {
        return YamlUtils.init(StationCommParamsYamlV1.class);
    }

    @Override
    public StationCommParamsYaml upgradeTo(StationCommParamsYamlV1 yaml, Long ... vars) {
        StationCommParamsYaml t = new StationCommParamsYaml();

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

    public String toString(StationCommParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    public StationCommParamsYamlV1 to(String s) {
        final StationCommParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
