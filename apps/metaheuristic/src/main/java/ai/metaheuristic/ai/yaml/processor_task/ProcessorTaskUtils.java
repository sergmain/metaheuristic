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
package ai.metaheuristic.ai.yaml.processor_task;

import ai.metaheuristic.commons.yaml.YamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Path;

@Slf4j
public class ProcessorTaskUtils {

    private static Yaml getYaml() {
        return YamlUtils.init(ProcessorCoreTask.class);
    }

    public static String toString(ProcessorCoreTask config) {
        return YamlUtils.toString(config, getYaml());
    }

    public static ProcessorCoreTask to(String s) {
        return (ProcessorCoreTask) YamlUtils.to(s, getYaml());
    }

    public static ProcessorCoreTask to(InputStream is) {
        return (ProcessorCoreTask) YamlUtils.to(is, getYaml());
    }

    public static ProcessorCoreTask to(Path file) {
        return (ProcessorCoreTask) YamlUtils.to(file, getYaml());
    }
}
