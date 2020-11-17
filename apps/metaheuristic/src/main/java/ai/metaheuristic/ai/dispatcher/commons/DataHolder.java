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

package ai.metaheuristic.ai.dispatcher.commons;

import ai.metaheuristic.ai.dispatcher.event.CommonEvent;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 11/1/2020
 * Time: 1:50 PM
 */
@Data
@Slf4j
public class DataHolder implements AutoCloseable {

    public DataHolder() {
        TxUtils.checkTxNotExists();
    }

    public final List<InputStream> inputStreams = new ArrayList<>();
    public final List<File> files = new ArrayList<>();
    public final List<CommonEvent> events = new ArrayList<>();

    @Override
    public void close() {
//        if (!events.isEmpty()) {
//            throw new IllegalStateException("There are not sended events: " + events);
//        }
        for (InputStream inputStream : inputStreams) {
            try {
                inputStream.close();
            }
            catch(Throwable th)  {
                log.warn("#448.020 Error while closing stream", th);
            }
        }
        for (File file : files) {
            try {
                Files.delete(file.toPath());
            }
            catch(Throwable th)  {
                log.warn("#448.040 Error while deleting file "+ file.getAbsolutePath(), th);
            }
        }
    }
}
