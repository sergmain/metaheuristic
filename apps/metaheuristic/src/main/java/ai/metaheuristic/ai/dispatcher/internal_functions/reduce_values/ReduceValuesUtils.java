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

package ai.metaheuristic.ai.dispatcher.internal_functions.reduce_values;

import ai.metaheuristic.ai.dispatcher.data.ReduceValuesData;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.Collection;

/**
 * @author Serge
 * Date: 11/13/2021
 * Time: 6:27 PM
 */
public class ReduceValuesUtils {

    @SneakyThrows
    public static ReduceValuesData.VariablesData loadData(File zipFile) {

        File tempDir = DirUtils.createMhTempDir("reduce-variables");
        if (tempDir==null) {
            throw new RuntimeException("Can't create metaheuristic-temp dir");
        }
        File zipDir = new File(tempDir, "zip");
        ZipUtils.unzipFolder(zipFile, zipDir);



        Collection<File> files =  FileUtils.listFiles(zipDir, new String[]{".zip"}, true);
        for (File f : files) {
            File tmp = DirUtils.createTempDir(tempDir, "load-data");
            File zipDataDir = new File(tmp, "zip");
            ZipUtils.unzipFolder(f, zipDataDir);
        }

        ReduceValuesData.VariablesData data = new ReduceValuesData.VariablesData();

        return data;
    }
}
