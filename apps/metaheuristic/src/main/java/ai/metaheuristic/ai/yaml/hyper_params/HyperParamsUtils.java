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
package ai.metaheuristic.ai.yaml.hyper_params;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

public class HyperParamsUtils {

    private static Yaml getYaml() {
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        return new Yaml(new Constructor(HyperParams.class), new Representer(), options);
    }

    public static String toString(HyperParams hyperParams) {
        return getYaml().dump(hyperParams);
    }

    public static HyperParams toHyperParamsYaml(String s) {
        return getYaml().load(s);
    }

    public static String toYaml(HyperParams hyperParams) {
        if (hyperParams==null) {
            return null;
        }
        String mapYaml;
        mapYaml = getYaml().dump(hyperParams.toSortedMap());
        return mapYaml;
    }


}
