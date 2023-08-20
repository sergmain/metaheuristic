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

package ai.metaheuristic.ai.yaml.communication.processor;

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
public class ProcessorCommParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<ProcessorCommParamsYamlV2, ProcessorCommParamsYamlV3, ProcessorCommParamsYamlUtilsV3, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ProcessorCommParamsYamlV2.class);
    }

    @NonNull
    @Override
    public ProcessorCommParamsYamlV3 upgradeTo(@NonNull ProcessorCommParamsYamlV2 src) {
        throw new UpgradeNotSupportedException("Upgrage Metaheuristic");
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void v2) {
        return null;
    }

    @Override
    public ProcessorCommParamsYamlUtilsV3 nextUtil() {
        return (ProcessorCommParamsYamlUtilsV3) ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.getForVersion(3);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull ProcessorCommParamsYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ProcessorCommParamsYamlV2 to(@NonNull String s) {
        final ProcessorCommParamsYamlV2 p = getYaml().load(s);
        return p;
    }

}
