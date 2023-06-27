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

package ai.metaheuristic.ai.dispatcher.event;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 12/21/2020
 * Time: 12:27 AM
 */
public class ResourceCloseEvent {
    public final List<InputStream> inputStreams = new ArrayList<>();
    public final List<Path> files = new ArrayList<>();
    public final List<Path> dirs = new ArrayList<>();

    public ResourceCloseEvent(List<InputStream> inputStreams, List<Path> files) {
        this(inputStreams, files, List.of());
    }

    public ResourceCloseEvent(List<InputStream> inputStreams, List<Path> files, List<Path> dirs ) {
        if (!inputStreams.isEmpty()) {
            this.inputStreams.addAll(inputStreams);
        }
        if (!files.isEmpty()) {
            this.files.addAll(files);
        }
        if (!dirs.isEmpty()) {
            this.dirs.addAll(dirs);
        }
    }

    public void clean() {
        this.inputStreams.clear();
        this.files.clear();
        this.dirs.clear();
    }
}
