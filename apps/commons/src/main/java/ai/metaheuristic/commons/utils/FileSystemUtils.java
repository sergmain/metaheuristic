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

package ai.metaheuristic.commons.utils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.Charset;

/**
 * @author Serge
 * Date: 12/19/2021
 * Time: 2:23 AM
 */
public class FileSystemUtils {

    public static void writeStringToFileWithSync(final File file, final String data, final Charset charset) throws IOException {
        // TODO P1 2022-04-24 change to modern usage with Path
//        try (OutputStream out = Files.newOutputStream(file.toPath(), CREATE, WRITE, READ, TRUNCATE_EXISTING)) {
        try (FileOutputStream out = FileUtils.openOutputStream(file, false)) {
            IOUtils.write(data, out, charset);
            out.flush();
            final FileDescriptor fd = out.getFD();
            if (fd.valid()) {
                fd.sync();
            }
        }
    }

    public static long copyFile(final File input, final OutputStream output) throws IOException {
        try (FileInputStream fis = new FileInputStream(input)) {
            return IOUtils.copyLarge(fis, output);
        }
    }

    public static void copyFileWithSync(final File srcFile, final File destFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(destFile)) {
            copyFile(srcFile, fos);
            fos.flush();
            final FileDescriptor fd = fos.getFD();
            if (fd.valid()) {
                fd.sync();
            }
        }
    }



}
