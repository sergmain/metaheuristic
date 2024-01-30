/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
import ai.metaheuristic.api.data.ParamsVersion;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
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

    public static ParamsVersion getParamsVersion(String s) {
        ParamsVersion yamlVersion = getYamlForVersion().load(s);
        return yamlVersion==null ? ConstsApi.PARAMS_VERSION_1 : yamlVersion;
    }

    private static Yaml getYamlForVersion() {
        Representer representer = new Representer(new DumperOptions());
        representer.getPropertyUtils().setSkipMissingProperties(true);
        representer.addClassTag(ParamsVersion.class, Tag.MAP);

        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(true);
        loaderOptions.setMaxAliasesForCollections(10);
        loaderOptions.setAllowRecursiveKeys(false);

        Constructor constructor = new Constructor(ParamsVersion.class, loaderOptions);

        Yaml yaml = new Yaml(constructor, representer);
        return yaml;
    }
}
