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

package ai.metaheuristic.ai.function;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.dispatcher.function.FunctionDataService;
import ai.metaheuristic.ai.dispatcher.function.FunctionCache;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
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
public class TestFunctionService {

    private static final String TEST_FUNCTION = "test.function:1.0";
    private static final String FUNCTION_PARAMS = "AAA";

    @Autowired
    private FunctionService functionService;

    @Autowired
    private FunctionRepository functionRepository;

    @Autowired
    private FunctionCache functionCache;

    @Autowired
    private FunctionDataService functionDataService;

    @Autowired
    private Globals globals;

    public Function function = null;

    @Test
    public void test() {
        SourceCodeParamsYaml.FunctionDefForSourceCode sd = new SourceCodeParamsYaml.FunctionDefForSourceCode();
        sd.code = TEST_FUNCTION;
        sd.params = null;
        TaskParamsYaml.FunctionConfig sc = functionService.getFunctionConfig(sd);

        assertNotNull(sc.params);
        final String[] split = StringUtils.split(sc.params);
        assertNotNull(split);
        assertEquals("Expected: ["+ FUNCTION_PARAMS +"], actual: " + Arrays.toString(split), 1, split.length);
        assertArrayEquals(new String[] {FUNCTION_PARAMS}, split);
    }

    @Before
    public void beforePreparingExperiment() {
        assertTrue(globals.isUnitTesting);

        long mills;
        byte[] bytes = "some program code".getBytes();
        function = functionRepository.findByCodeForUpdate(TEST_FUNCTION);
        if (function == null) {
            Function s = new Function();
            FunctionConfigYaml sc = new FunctionConfigYaml();
            sc.code = TEST_FUNCTION;
            sc.type = "test";
            sc.env = "python-3";
            sc.file = "predict-filename.txt";
            sc.info.length = bytes.length;
            sc.checksum = "sha2";
            sc.skipParams = false;
            sc.params = FUNCTION_PARAMS;

            s.setCode(TEST_FUNCTION);
            s.setType("test");
            s.params = FunctionConfigYamlUtils.BASE_YAML_UTILS.toString(sc);

            mills = System.currentTimeMillis();
            log.info("Start functionRepository.save() #2");
            function = functionCache.save(s);
            log.info("functionRepository.save() #2 was finished for {}", System.currentTimeMillis() - mills);

            mills = System.currentTimeMillis();
            log.info("Start binaryDataService.save() #2");
            functionDataService.save(new ByteArrayInputStream(bytes), bytes.length, s.getCode());
            log.info("binaryDataService.save() #2 was finished for {}", System.currentTimeMillis() - mills);
        }
    }

    @After
    public void afterPreparingExperiment() {
        long mills = System.currentTimeMillis();
        log.info("Start after()");
        if (function != null) {
            try {
                functionCache.delete(function.getId());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
            try {
                functionDataService.deleteByFunctionCode(function.getCode());
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        System.out.println("Was finished correctly");
        log.info("after() was finished for {}", System.currentTimeMillis() - mills);
    }

}
