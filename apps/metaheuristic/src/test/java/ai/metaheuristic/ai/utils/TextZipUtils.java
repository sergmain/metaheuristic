/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.utils;

import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import org.junit.Test;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static ai.metaheuristic.ai.launchpad.batch.process_resource.BatchTopLevelService.VALIDATE_ZIP_FUNCTION;
import static org.junit.Assert.assertFalse;

/**
 * @author Serge
 * Date: 6/5/2019
 * Time: 12:24 PM
 */
public class TextZipUtils {

    @Test
    public void validateZip() throws IOException {
        File dir = DirUtils.createTempDir("test-unzip-");

        final File tempZipFile = File.createTempFile("temp-zip-file-", ".zip", dir);
        try (FileOutputStream fos = new FileOutputStream(tempZipFile);
             InputStream is = TextZipUtils.class.getResourceAsStream("/bin/test-zip.zip")) {
            FileCopyUtils.copy(is, fos);
        }
        List<String> errors = ZipUtils.validate(tempZipFile, VALIDATE_ZIP_FUNCTION);

        System.out.println(errors);

        assertFalse(errors.isEmpty());

    }
}
