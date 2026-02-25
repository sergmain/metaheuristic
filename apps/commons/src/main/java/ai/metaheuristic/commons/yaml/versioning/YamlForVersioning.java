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

package ai.metaheuristic.commons.yaml.versioning;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.data.ParamsVersion;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;

import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 9:52 PM
 */
public class YamlForVersioning {

    private static final Pattern VERSION_NUMBER_PATTERN = Pattern.compile("^version: (?<version>\\d)\\s*$");

    public static ParamsVersion getParamsVersion(String s) {
        LineIterator it = IOUtils.lineIterator(new StringReader(s));
        while (it.hasNext()) {
            String line = it.next();
            Matcher m = VERSION_NUMBER_PATTERN.matcher(line);
            boolean b = m.find();
            if (b) {
                int version = Integer.parseInt(m.group("version"));
                return new ParamsVersion(version);
            }
        }
        return ConstsApi.PARAMS_VERSION_1;
    }

/*
    public static ParamsVersion getParamsVersion_old(String s) {
        ParamsVersion yamlVersion = getYamlForVersion().load(s);
        return yamlVersion==null ? ConstsApi.PARAMS_VERSION_1 : yamlVersion;
    }

    @SuppressWarnings("deprecation")
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
*/
}
