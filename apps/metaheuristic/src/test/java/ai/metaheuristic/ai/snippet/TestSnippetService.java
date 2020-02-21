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

package ai.metaheuristic.ai.snippet;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.launchpad.beans.Function;
import ai.metaheuristic.ai.launchpad.repositories.SnippetRepository;
import ai.metaheuristic.ai.launchpad.snippet.SnippetDataService;
import ai.metaheuristic.ai.launchpad.snippet.SnippetCache;
import ai.metaheuristic.ai.launchpad.snippet.SnippetService;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * @author Serge
 * Date: 7/12/2019
 * Time: 5:42 PM
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
@Slf4j
public class TestSnippetService {

    private static final String TEST_SNIPPET = "test.function:1.0";
    private static final String SNIPPET_PARAMS = "AAA";

    @Autowired
    private SnippetService snippetService;

    @Autowired
    private SnippetRepository snippetRepository;

    @Autowired
    private SnippetCache snippetCache;

    @Autowired
    private SnippetDataService snippetDataService;

    @Autowired
    private Globals globals;

    public Function function = null;

    @Test
    public void test() {
        SourceCodeParamsYaml.FunctionDefForSourceCode sd = new SourceCodeParamsYaml.FunctionDefForSourceCode();
        sd.code = TEST_SNIPPET;
        sd.params = null;
        TaskParamsYaml.FunctionConfig sc = snippetService.getSnippetConfig(sd);

        assertNotNull(sc.params);
        final String[] split = StringUtils.split(sc.params);
        assertNotNull(split);
        assertEquals("Expected: ["+SNIPPET_PARAMS+"], actual: " + Arrays.toString(split), 1, split.length);
        assertArrayEquals(new String[] {SNIPPET_PARAMS}, split);
    }

    @Before
    public void beforePreparingExperiment() {
        assertTrue(globals.isUnitTesting);

        long mills;
        byte[] bytes = "some program code".getBytes();
        function = snippetRepository.findByCodeForUpdate(TEST_SNIPPET);
        if (function == null) {
            Function s = new Function();
            FunctionConfigYaml sc = new FunctionConfigYaml();
            sc.code = TEST_SNIPPET;
            sc.type = "test";
            sc.env = "python-3";
            sc.file = "predict-filename.txt";
            sc.info.length = bytes.length;
            sc.checksum = "sha2";
            sc.skipParams = false;
            sc.params = SNIPPET_PARAMS;

            s.setCode(TEST_SNIPPET);
            s.setType("test");
            s.params = FunctionConfigYamlUtils.BASE_YAML_UTILS.toString(sc);

            mills = System.currentTimeMillis();
            log.info("Start snippetRepository.save() #2");
            function = snippetCache.save(s);
            log.info("stationsRepository.save() #2 was finished for {}", System.currentTimeMillis() - mills);

            mills = System.currentTimeMillis();
            log.info("Start binaryDataService.save() #2");
            snippetDataService.save(new ByteArrayInputStream(bytes), bytes.length, s.getCode());
            log.info("binaryDataService.save() #2 was finished for {}", System.currentTimeMillis() - mills);
        }
    }

    @After
    public void afterPreparingExperiment() {
        long mills = System.currentTimeMillis();
        log.info("Start after()");
        if (function != null) {
            try {
                snippetCache.delete(function.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
            try {
                snippetDataService.deleteBySnippetCode(function.getCode());
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        System.out.println("Was finished correctly");
        log.info("after() was finished for {}", System.currentTimeMillis() - mills);
    }

}
