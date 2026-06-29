/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.source_code;
import ai.metaheuristic.api.EnumsApi;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest;
import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.ai.spi.MhSpi;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ch.qos.logback.classic.LoggerContext;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 3/14/2020
 * Time: 8:53 PM
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class TestSingleInternalFunction extends FeatureMethods {

    @org.junit.jupiter.api.io.TempDir
    static Path tempDir;

    @Autowired private TaskRepositoryForTest taskRepositoryForTest;
    @Autowired private TaskRepository taskRepository;

    @Override
    @SneakyThrows
        public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/for-testing-single-internal-function.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @Test
    public void test() {
        System.out.println("start step_0_0_produce_tasks_and_start()");
        step_0_0_produce_tasks_and_start();

        List<Object[]> list = taskRepositoryForTest.findAllExecStateAndParamsByExecContextId(getExecContextForTest().id);
        assertEquals(2, list.size());

        final List<String> codes = List.of(getFunctionCode(list.get(0)[0]), getFunctionCode(list.get(1)[0]));
        assertTrue(codes.contains(CommonConsts.MH_FINISH_FUNCTION));
        assertTrue(codes.contains(Consts.MH_BATCH_SPLITTER_FUNCTION));
    }

    private String getFunctionCode(Object object) {
        TaskImpl task = taskRepository.findByIdReadOnly((Long) object);
        assertNotNull(task);
        TaskParamsYaml taskParamsYaml = task.getTaskParamsYaml();
        return taskParamsYaml.task.function.code;
    }
}



