/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

package ai.metaheuristic.ai.processor.actors;

import ai.metaheuristic.ai.Consts;
import org.apache.http.Header;
import org.springframework.lang.Nullable;

import java.io.File;

/**
 * @author Serge
 * Date: 8/23/2019
 * Time: 5:46 PM
 */
class DownloadUtils {
    static boolean isChunkConsistent(File partFile, Header[] headers) {
        String sizeAsStr = getHeader(headers, Consts.HEADER_MH_CHUNK_SIZE);
        if (sizeAsStr==null || sizeAsStr.isBlank()) {
            return true;
        }
        long expectedSize = Long.parseLong(sizeAsStr);
        return partFile.length()==expectedSize;
    }

    static boolean isLastChunk(Header[] headers) {
        return "true".equals(getHeader(headers, Consts.HEADER_MH_IS_LAST_CHUNK));
    }

    @Nullable
    private static String getHeader(Header[] headers, String name) {
        for (Header header : headers) {
            if (name.equals(header.getName())) {
                return header.getValue();
            }
        }
        return null;
    }
}
