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

package ai.metaheuristic.ai.yaml.pilot;

import ai.metaheuristic.ai.launchpad.batch.beans.BatchParams;
import ai.metaheuristic.commons.yaml.YamlUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;

/**
 * @author Serge
 * Date: 5/29/2019
 * Time: 11:51 PM
 */
public class BatchParamsUtils {

    private static Yaml getYaml() {
        return YamlUtils.init(BatchParams.class);
    }

    public static String toString(BatchParams config) {
        return YamlUtils.toString(config, getYaml());
    }

    public static BatchParams to(String s) {

        return (BatchParams) YamlUtils.to(s, getYaml());
    }

    public static BatchParams to(InputStream is) {
        return (BatchParams) YamlUtils.to(is, getYaml());
    }

    public static BatchParams to(File file) {
        return (BatchParams) YamlUtils.to(file, getYaml());
    }
}
