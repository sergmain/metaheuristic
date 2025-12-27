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

package ai.metaheuristic.ai.yaml.communication.keep_alive;

import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.exceptions.UpgradeNotSupportedException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 10/03/2019
 * Time: 6:02 PM
 */
public class KeepAliveRequestParamYamlUtilsV2 extends
        AbstractParamsYamlUtils<KeepAliveRequestParamYamlV2, KeepAliveRequestParamYamlV3, KeepAliveRequestParamYamlUtilsV3, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(KeepAliveRequestParamYamlV2.class);
    }

    @NonNull
    @Override
    public KeepAliveRequestParamYamlV3 upgradeTo(@NonNull KeepAliveRequestParamYamlV2 src) {
        throw new UpgradeNotSupportedException("Upgrade all processors to new version of Metaheuristic");
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        throw new DowngradeNotSupportedException();
    }

    @Override
    public KeepAliveRequestParamYamlUtilsV3 nextUtil() {
        return (KeepAliveRequestParamYamlUtilsV3) KeepAliveRequestParamYamlUtils.BASE_YAML_UTILS.getForVersion(3);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull KeepAliveRequestParamYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public KeepAliveRequestParamYamlV2 to(@NonNull String s) {
        final KeepAliveRequestParamYamlV2 p = getYaml().load(s);
        return p;
    }

}
