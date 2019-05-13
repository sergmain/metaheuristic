/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.yaml.input_resource_param;

import ai.metaheuristic.api.v1.data.InputResourceParam;
import aiai.apps.commons.yaml.YamlUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.InputStream;

public class InputResourceParamUtils {

    private static Yaml yaml;

    static {
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        yaml = new Yaml(new Constructor(InputResourceParam.class), new Representer(), options);
    }

    public static String toString(InputResourceParam params) {
        return yaml.dump(params);
    }

    public static InputResourceParam to(InputStream is) {
        return (InputResourceParam) YamlUtils.to(is, yaml);
    }

    public static InputResourceParam to(String s) {
        return yaml.load(s);
    }

}
