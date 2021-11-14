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
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.yaml.metadata_aggregate_function.MetadataAggregateFunctionParamsYaml;
import ai.metaheuristic.ai.yaml.metadata_aggregate_function.MetadataAggregateFunctionParamsYamlUtils;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import static ai.metaheuristic.ai.Consts.MH_METADATA_YAML_FILE_NAME;

/**
 * @author Serge
 * Date: 11/13/2021
 * Time: 6:27 PM
 */
public class ReduceValuesUtils {

    @SneakyThrows
    public static ReduceValuesData.VariablesData loadData(File zipFile) {

        File tempDir = DirUtils.createMhTempDir("reduce-variables-");
        if (tempDir==null) {
            throw new RuntimeException("Can't create temp dir in metaheuristic-temp dir");
        }
        File zipDir = new File(tempDir, "zip");
        ZipUtils.unzipFolder(zipFile, zipDir);



        Collection<File> files =  FileUtils.listFiles(zipDir, new String[]{"zip"}, true);
        for (File f : files) {
            File tmp = DirUtils.createTempDir(tempDir, "load-data");
            File zipDataDir = new File(tmp, "zip");
            ZipUtils.unzipFolder(f, zipDataDir);

            Collection<File> top = FileUtils.listFiles(zipDataDir, null, false);
            if (top.isEmpty()) {
                throw new RuntimeException("can't find any dir in " + zipDataDir.getAbsolutePath());
            }
            File topD = top.stream().findFirst().orElseThrow(()->new RuntimeException("can't find any dir in stream"));

            Collection<File> ctxDirs =  FileUtils.listFiles(topD, null, false);

            ReduceValuesData.PermutedVariables pvs = new ReduceValuesData.PermutedVariables();
            for (File ctxDir : ctxDirs) {
                File metadata = new File(ctxDir, MH_METADATA_YAML_FILE_NAME);
                String yaml = FileUtils.readFileToString(metadata, StandardCharsets.UTF_8);
                MetadataAggregateFunctionParamsYaml mafpy = MetadataAggregateFunctionParamsYamlUtils.BASE_YAML_UTILS.to(yaml);

                for (File file : FileUtils.listFiles(ctxDir, null, false)) {
                    if (file.getName().equals(MH_METADATA_YAML_FILE_NAME)) {
                        continue;
                    }
                    String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
//                    String varName = mafpy.mapping.stream().filter(o->o.)
                }

                if ("1".equals(ctxDir.getName())) {
                    Collection<File> ctxDirs1 =  FileUtils.listFiles(topD, null, false);
                }
            }

            int i=0;
        }

        ReduceValuesData.VariablesData data = new ReduceValuesData.VariablesData();

        return data;
    }
}
