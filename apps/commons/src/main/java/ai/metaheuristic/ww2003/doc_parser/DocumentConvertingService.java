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

package ai.metaheuristic.ww2003.doc_parser;

import ai.metaheuristic.ww2003.utils.XmlUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class DocumentConvertingService {

    public static InputStream convertToUTF8(InputStream inputStream, String filename) throws IOException {
        return convertToUTF8(inputStream, filename, true);
    }

    public static InputStream convertToUTF8(InputStream inputStream, String filename, boolean isMemory) throws IOException {
        if (!inputStream.markSupported()) {
            inputStream = new BufferedInputStream(inputStream);
        }
        inputStream.mark(500);
        XmlUtils.ConvertingResult convertingResult = XmlUtils.convertXmlToUtf8(inputStream, isMemory);
        switch (convertingResult.status) {
            case ALREADY_IN_UTF8:
                inputStream.reset();
                return new BOMInputStream(inputStream);
            case CONVERTED:
                if (isMemory) {
                    return IOUtils.toInputStream(convertingResult.doc, StandardCharsets.UTF_8);
                }
                else {
                    return convertingResult.inputStream;
                }
            case ERROR:
                throw new IOException("111.020 error while converting file " + filename + ", isMemory: " +isMemory+ ", " + convertingResult.error);
            default:
                throw new IOException("111.040 unknown status " + convertingResult.status);
        }
    }
}