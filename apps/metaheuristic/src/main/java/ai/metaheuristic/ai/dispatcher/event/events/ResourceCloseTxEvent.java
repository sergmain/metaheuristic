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

package ai.metaheuristic.ai.dispatcher.event.events;

import lombok.NoArgsConstructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 12/21/2020
 * Time: 12:26 AM
 */
@NoArgsConstructor
public class ResourceCloseTxEvent {
    private final List<InputStream> inputStreams = new ArrayList<>();
    private final List<Path> files = new ArrayList<>();
    private final List<Path> dirs = new ArrayList<>();

    public ResourceCloseTxEvent(InputStream inputStream) {
        this.inputStreams.add(inputStream);
    }

    public ResourceCloseTxEvent(Path tempFile) {
        add(tempFile);
    }

    public ResourceCloseTxEvent(InputStream is, Path tempFile) {
        this.inputStreams.add(is);
        add(tempFile);
    }

    public ResourceCloseTxEvent(List<InputStream> iss, Path tempFile) {
        this.inputStreams.addAll(iss);
        add(tempFile);
    }

    public void add(InputStream inputStream) {
        this.inputStreams.add(inputStream);
    }

    public void add(Path path) {
        if (Files.isDirectory(path)) {
            this.dirs.add(path);
        }
        else {
            this.files.add(path);
        }
    }

    public ResourceCloseEvent to() {
        return new ResourceCloseEvent(inputStreams, files, dirs);
    }

    public void clean() {
        this.inputStreams.clear();
        this.files.clear();
        this.dirs.clear();
    }

}
