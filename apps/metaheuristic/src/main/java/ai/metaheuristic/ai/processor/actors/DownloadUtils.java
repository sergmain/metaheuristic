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

package ai.metaheuristic.ai.processor.actors;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import org.apache.commons.io.FileUtils;
import org.apache.hc.core5.http.Header;
import org.springframework.lang.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.*;

/**
 * @author Serge
 * Date: 8/23/2019
 * Time: 5:46 PM
 */
public class DownloadUtils {

    public static boolean isChunkConsistent(Path partFile, Header[] headers) throws IOException {
        String sizeAsStr = getHeader(headers, Consts.HEADER_MH_CHUNK_SIZE);
        if (sizeAsStr==null || sizeAsStr.isBlank()) {
            return true;
        }
        long expectedSize = Long.parseLong(sizeAsStr);
        return Files.size(partFile)==expectedSize;
    }

    public static boolean isLastChunk(Header[] headers) {
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

    public static void combineParts(Path partBaseFile, Path tempFile, int idx) throws IOException {
        try (OutputStream fos = Files.newOutputStream(tempFile, CREATE, TRUNCATE_EXISTING, WRITE, SYNC); BufferedOutputStream bos = new BufferedOutputStream(fos, 1_000_000)) {
            for (int i = 0; i <= idx; i++) {
                Path p = Path.of(partBaseFile.toAbsolutePath() + "." + i + ".tmp");
                if (Files.size(p)==0) {
                    continue;
                }
                Files.copy(p, bos);
            }
            bos.flush();
        }
    }

    public static void combineParts(AssetFile assetFile, File tempFile, int idx) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            for (int i = 0; i <= idx; i++) {
                final File input = new File(assetFile.file.toAbsolutePath() + "." + i + ".tmp");
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
