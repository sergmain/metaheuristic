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

package ai.metaheuristic.ai.yaml.communication.dispatcher;

import ai.metaheuristic.commons.exceptions.UpgradeNotSupportedException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 10/03/2019
 * Time: 6:02 PM
 */
public class DispatcherCommParamsYamlUtilsV1 extends
        AbstractParamsYamlUtils<DispatcherCommParamsYamlV1, DispatcherCommParamsYamlV2, DispatcherCommParamsYamlUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(DispatcherCommParamsYamlV1.class);
    }

    @Override
    public DispatcherCommParamsYamlV2 upgradeTo(DispatcherCommParamsYamlV1 v1) {
        throw new UpgradeNotSupportedException();
    }

    @Override
    public Void downgradeTo(Void yaml) {
        return null;
    }

    @Override
    public DispatcherCommParamsYamlUtilsV2 nextUtil() {
        return (DispatcherCommParamsYamlUtilsV2) DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(DispatcherCommParamsYamlV1 yaml) {
        yaml.checkIntegrity();

        return getYaml().dump(yaml);
    }

    @Override
    public DispatcherCommParamsYamlV1 to(String s) {
        final DispatcherCommParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
