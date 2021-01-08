/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
package ai.metaheuristic.ai.yaml.function_exec;

import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.yaml.YamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;

@Slf4j
public class FunctionExecUtils {

    private static Yaml getYaml() {
        return YamlUtils.init(FunctionApiData.FunctionExec.class);
    }

    public static String toString(FunctionApiData.FunctionExec config) {
        return YamlUtils.toString(config, getYaml());
    }

    @Nullable
    public static FunctionApiData.FunctionExec to(@Nullable String s) {
        return (FunctionApiData.FunctionExec) YamlUtils.toNullable(s, getYaml());
    }

    public static FunctionApiData.FunctionExec to(InputStream is) {
        return (FunctionApiData.FunctionExec) YamlUtils.to(is, getYaml());
    }

    public static FunctionApiData.FunctionExec to(File file) {
        return (FunctionApiData.FunctionExec) YamlUtils.to(file, getYaml());
    }
}
