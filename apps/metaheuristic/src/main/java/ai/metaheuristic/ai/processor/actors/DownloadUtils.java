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

package ai.metaheuristic.ai.processor.actors;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import org.apache.commons.io.FileUtils;
import org.apache.http.Header;
import org.springframework.lang.Nullable;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;

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

    static void combineParts(AssetFile assetFile, File tempFile, int idx) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            for (int i = 0; i <= idx; i++) {
                final File input = new File(assetFile.file.getAbsolutePath() + "." + i + ".tmp");
                if (input.length()==0) {
                    continue;
                }
                FileUtils.copyFile(input, fos);
            }
            fos.flush();
            final FileDescriptor fd = fos.getFD();
            if (fd.valid()) {
                fd.sync();
            }
        }
    }
}
