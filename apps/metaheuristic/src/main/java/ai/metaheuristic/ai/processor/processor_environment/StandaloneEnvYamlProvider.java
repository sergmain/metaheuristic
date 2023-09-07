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

package ai.metaheuristic.ai.processor.processor_environment;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * @author Sergio Lissner
 * Date: 8/19/2023
 * Time: 7:14 PM
 */
public class StandaloneEnvYamlProvider implements EnvYamlProvider {
    public static final String YAML = """
            version: 4
            processors:
              - code: proc-01
            envs:
              - code: java-11
                exec: java -Dfile.encoding=UTF-8 -jar
                verify:
                  run: false
                  params: --version
              - code: java-17
                exec: java -Dfile.encoding=UTF-8 -jar
                verify:
                  run: false
                  params: --version
            quotas:
              disabled: true
            """;
    @Override
    public InputStream provide() {
        return new ByteArrayInputStream(YAML.getBytes());
    }
}
