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

package ai.metaheuristic.ai.batch;

import ai.metaheuristic.ai.exceptions.BatchResourceProcessingException;
import ai.metaheuristic.ai.dispatcher.internal_functions.resource_splitter.ResourceSplitterFunction;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.commons.utils.DirUtils;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @author Serge
 * Date: 11/19/2019
 * Time: 7:27 PM
 */
public class TestBatchConfigYamlFile {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testParsing() throws IOException {

        try (InputStream is = TestBatchConfigYamlFile.class.getResourceAsStream("/yaml/batch/bad-config.yaml")) {
            ResourceSplitterFunction.getMainDocument(is);
        }
    }

    @Test
    public void testDirContent_01() throws IOException {
        File dir=null;
        try {
            dir = DirUtils.createTempDir("batch-config-test-");
            File artifact = new File(dir, ConstsApi.ARTIFACTS_DIR);
            FileUtils.write(new File(artifact, "aaa.xml"), "aaa", StandardCharsets.UTF_8);
            FileUtils.write(new File(artifact, "bbb.xml"), "bbb", StandardCharsets.UTF_8);
            FileUtils.write(new File(artifact, "config.yaml"), "mainDocument: ccc.xml", StandardCharsets.UTF_8);

            thrown.expect(BatchResourceProcessingException.class);
            File f = ResourceSplitterFunction.getMainDocumentFileFromConfig(artifact, Map.of());
        }
        finally {
            if (dir!=null) {
                FileUtils.deleteDirectory(dir);
            }
        }
    }

    @Test
    public void testDirContent_02() throws IOException {
        File dir=null;
        try {
            dir = DirUtils.createTempDir("batch-config-test-");
            File artifact = new File(dir, ConstsApi.ARTIFACTS_DIR);
            FileUtils.write(new File(artifact, "aaa1.xml"), "aaa", StandardCharsets.UTF_8);
            FileUtils.write(new File(artifact, "bbb1.xml"), "bbb", StandardCharsets.UTF_8);
            FileUtils.write(new File(artifact, "config.yaml"), "mainDocument: aaa.xml", StandardCharsets.UTF_8);

//            thrown.expect(BatchResourceProcessingException.class);
            File f = ResourceSplitterFunction.getMainDocumentFileFromConfig(artifact, Map.of("artifacts/aaa1.xml", "artifacts/aaa.xml", "artifacts/bbb1.xml", "artifacts/bbb.xml"));
        }
        finally {
            if (dir!=null) {
                FileUtils.deleteDirectory(dir);
            }
        }
    }
}
