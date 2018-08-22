/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.yaml.hyper_params;

import aiai.ai.yaml.sequence.SequenceYaml;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

public class HyperParamsUtils {

    private static Yaml yaml;

    static {
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        yaml = new Yaml(new Constructor(HyperParams.class), new Representer(), options);
    }

    public static String toString(HyperParams hyperParams) {
        return yaml.dump(hyperParams);
    }

    public static SequenceYaml toHyperParamsYaml(String s) {
        return yaml.load(s);
    }

    public static String toYaml(HyperParams hyperParams) {
        if (hyperParams==null) {
            return null;
        }
        String mapYaml;
        mapYaml = yaml.dump(hyperParams.toSortedMap());
        return mapYaml;
    }


}
