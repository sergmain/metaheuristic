/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

package ai.metaheuristic.commons.yaml.versioning;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.data.YamlVersion;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 9:52 PM
 */
public class YamlForVersioning {

    public static Yaml getYamlForVersion() {
        Representer representer = new Representer();
        representer.getPropertyUtils().setSkipMissingProperties(true);
        representer.addClassTag(YamlVersion.class, Tag.MAP);

        Constructor constructor = new Constructor(YamlVersion.class);

        //noinspection UnnecessaryLocalVariable
        Yaml yaml = new Yaml(constructor, representer);
        return yaml;
    }

    public static YamlVersion getYamlVersion(String s) {
        YamlVersion yamlVersion = getYamlForVersion().load(s);
        return yamlVersion==null ? ConstsApi.YAML_VERSION_1 : yamlVersion;
    }
}
