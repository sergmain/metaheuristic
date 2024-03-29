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

package ai.metaheuristic.ai.processor.processor_environment;

import lombok.SneakyThrows;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Sergio Lissner
 * Date: 8/19/2023
 * Time: 7:13 PM
 */
public class FileEnvYamlProvider implements EnvYamlProvider {
    public final Path path;

    public FileEnvYamlProvider(Path path) {
        this.path = path;
    }

    @SneakyThrows
    @Override
    public InputStream provide() {
        return Files.newInputStream(path);
    }
}
