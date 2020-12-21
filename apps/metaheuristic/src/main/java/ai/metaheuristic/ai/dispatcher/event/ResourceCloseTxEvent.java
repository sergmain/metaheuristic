/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 12/21/2020
 * Time: 12:26 AM
 */
public class ResourceCloseTxEvent {
    public final List<InputStream> inputStreams = new ArrayList<>();
    public final List<File> files = new ArrayList<>();

    public ResourceCloseTxEvent(InputStream inputStream) {
        this.inputStreams.add(inputStream);
    }

    public ResourceCloseTxEvent(File tempFile) {
        this.files.add(tempFile);
    }

    public ResourceCloseTxEvent(InputStream is, File tempFile) {
        this.inputStreams.add(is);
        this.files.add(tempFile);
    }

    public ResourceCloseEvent to() {
        return new ResourceCloseEvent(inputStreams, files);
    }
}
